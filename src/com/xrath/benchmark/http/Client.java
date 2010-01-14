package com.xrath.benchmark.http;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import com.xrath.benchmark.http.impl.GzipDecompressingEntity;
import com.xrath.benchmark.http.util.ZeroBasedMap;

/**
 * 
 * @author rath
 *
 */
public class Client extends Thread {
	private int repeatCount;
	private List<Map<String, Object>> requests;
	private DefaultHttpClient client;
	private String host;
	
	private int totalRequestCount = 0;
	private int errorConnect = 0;
	private int errorSocketTimeout = 0;
	private int errorUnknown = 0;
	
	private int okCount = 0;
	private long okTotalTime = 0L;
	private long okMinTime = Long.MAX_VALUE;
	private long okMaxTime = Long.MIN_VALUE;

	private ZeroBasedMap<Integer, Long> statusAggregate = new ZeroBasedMap<Integer, Long>();
	
	// URI base variables
	private ZeroBasedMap<String, Long> uriTotalRequest = new ZeroBasedMap<String, Long>();
	private ZeroBasedMap<String, Long> uriOkCount = new ZeroBasedMap<String, Long>();
	private ZeroBasedMap<String, Long> uriOkTotalTime = new ZeroBasedMap<String, Long>();
	private ZeroBasedMap<String, Long> uriOkMinTime = new ZeroBasedMap<String, Long>();
	private ZeroBasedMap<String, Long> uriOkMaxTime = new ZeroBasedMap<String, Long>();
	
	private Map<String, ZeroBasedMap<Integer, Long>> uriStatusAggregate = new HashMap<String, ZeroBasedMap<Integer, Long>>();
	private String hostHeader;
	
	public Client() {
		HttpParams httpParams = new BasicHttpParams();
		httpParams.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 4000);
		httpParams.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 8000);
		httpParams.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, true);
		client = new DefaultHttpClient(httpParams);
		
		client.addResponseInterceptor(new HttpResponseInterceptor() {
			@Override
			public void process(HttpResponse response, HttpContext context)
					throws HttpException, IOException {
				HttpEntity entity = response.getEntity();
				Header ceheader = entity.getContentEncoding();
				if (ceheader != null) {
					HeaderElement[] codecs = ceheader.getElements();
					for (int i = 0; i < codecs.length; i++) {
						if (codecs[i].getName().equalsIgnoreCase("gzip")) {
							response.setEntity(new GzipDecompressingEntity(response.getEntity()));
							return;
						}
					}
				}
			}
		});
	}
	
	public void run() {	
		int count = 0;
		boolean go = true;
		while(go) {
			for(Map<String, Object> req : requests) {
				
				try {
					requestImpl(req);
				} catch( HttpHostConnectException e ) {
					// connect failed.
					System.err.println( "ERROR: Connection refused from " + this.host);
					errorConnect++;
				} catch( SocketTimeoutException e ) {
					System.err.println( "ERROR: Socket timeout.");
					errorSocketTimeout++;
				} catch( Exception e ) {
					System.err.println( "ERROR: " + e);
					errorUnknown++;
				} 
				totalRequestCount++;
				count++;
				if( repeatCount!=-1 && count >= repeatCount ) {
					go = false;
					break;
				}
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	protected void requestImpl( Map<String, Object> req ) throws IOException {
		HttpRequestBase base = null;
		String method = (String)req.get("METHOD");
		String uri = (String)req.get("REQUEST_URI");
		if( method.equals("GET") ) 
			base = new HttpGet(host + uri + createQueryString((Map<String, String>) req.get("PARAMETERS")));
		else
		if( method.equals("POST") ) 
			base = new HttpPost(host + uri);
		else
			throw new UnsupportedOperationException("GET/POST only, but " + method);
			
		Map<String, String> headers = (Map<String, String>)req.get("HEADERS");
		for(Map.Entry<String, String> entry : headers.entrySet()) { 
			if( entry.getKey().equalsIgnoreCase("Content-Length") )
				continue;
			if( entry.getKey().equalsIgnoreCase("Host") ) {
				base.addHeader("Host", this.hostHeader);
			} else {
				base.addHeader(entry.getKey(), entry.getValue());
			}
		}
		
		Map<String, String> parameters = (Map<String, String>)req.get("PARAMETERS");
		if( base.getMethod().equals("POST") ) {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			for(Map.Entry<String, String> entry : parameters.entrySet()) {
				params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
			}
			UrlEncodedFormEntity formData = new UrlEncodedFormEntity(params, "UTF-8");
			((HttpPost)base).setEntity(formData);
		}

		long l0 = System.currentTimeMillis();
		HttpResponse response = client.execute(base);
		long l1 = System.currentTimeMillis();
		HttpEntity entity = response.getEntity();
		entity.consumeContent();
		
		long time = l1-l0;
		
		StatusLine sl = response.getStatusLine();
		int code = sl.getStatusCode();
		
		// Global statistics
		if( !statusAggregate.containsKey(code) ) {
			statusAggregate.put(code, 0L);
		}
		statusAggregate.put(code, statusAggregate.get(code)+1);
		
		if( code==200 ) {
			okCount++;
			okTotalTime += time;
			okMinTime = Math.min(okMinTime, time);
			okMaxTime = Math.max(okMaxTime, time);
		}

		// URI based statistics
		if( !uriStatusAggregate.containsKey(uri) ) {
			ZeroBasedMap<Integer, Long> uriAgg = new ZeroBasedMap<Integer, Long>();
			uriStatusAggregate.put(uri, uriAgg);
		}
		ZeroBasedMap<Integer, Long> uriAgg = uriStatusAggregate.get(uri);
		if( uriAgg==null ) {
			uriStatusAggregate.put(uri, new ZeroBasedMap<Integer, Long>());
			uriAgg = uriStatusAggregate.get(uri);
		}
		uriAgg.add(code, 1L);
		
		if( code==200 ) {
			if( !uriOkMinTime.containsKey(uri) )
				uriOkMinTime.put(uri, Long.MAX_VALUE);
			
			uriOkCount.add(uri, 1L);
			uriOkTotalTime.add(uri, time);
			uriOkMinTime.put(uri, Math.min(uriOkMinTime.get(uri), time));
			uriOkMaxTime.put(uri, Math.max(uriOkMaxTime.get(uri), time));
		}
		
		uriTotalRequest.add(uri, 1L);
	}
	
	private String createQueryString( Map<String, String> params ) {
		if( params.size()==0 ) 
			return "";
		StringBuffer sb = new StringBuffer("?");
		for(Map.Entry<String, String> e : params.entrySet()) {
			sb.append(e.getKey());
			sb.append("=");
			sb.append(e.getValue());
			sb.append("&");
		}
		return sb.toString();
	}

	public void setRepeatCount(int repeatCount) {
		this.repeatCount = repeatCount;
	}

	public int getRepeatCount() {
		return repeatCount;
	}

	public void setRequests(List<Map<String, Object>> requests) {
		this.requests = requests;
	}

	public List<Map<String, Object>> getRequests() {
		return requests;
	}

	public void setHost(String host) throws MalformedURLException {
//		this.host = host;
		URL url = new URL(host);
		this.host = url.getProtocol() + "://" + url.getHost();
		this.hostHeader = url.getHost();
		if( url.getPort()!=-1 ) {
			this.host += ":" + url.getPort();
		}
	}

	public String getHost() {
		return host;
	}
	
	public int getTotalRequestCount() {
		return totalRequestCount;
	}

	public void setTotalRequestCount(int totalRequestCount) {
		this.totalRequestCount = totalRequestCount;
	}

	public int getErrorConnect() {
		return errorConnect;
	}

	public void setErrorConnect(int errorConnect) {
		this.errorConnect = errorConnect;
	}

	public int getErrorSocketTimeout() {
		return errorSocketTimeout;
	}

	public void setErrorSocketTimeout(int errorSocketTimeout) {
		this.errorSocketTimeout = errorSocketTimeout;
	}

	public int getErrorUnknown() {
		return errorUnknown;
	}

	public void setErrorUnknown(int errorUnknown) {
		this.errorUnknown = errorUnknown;
	}

	public int getOkCount() {
		return okCount;
	}

	public void setOkCount(int okCount) {
		this.okCount = okCount;
	}

	public long getOkTotalTime() {
		return okTotalTime;
	}

	public void setOkTotalTime(long okTotalTime) {
		this.okTotalTime = okTotalTime;
	}

	public long getOkMinTime() {
		return okMinTime;
	}

	public void setOkMinTime(long okMinTime) {
		this.okMinTime = okMinTime;
	}

	public long getOkMaxTime() {
		return okMaxTime;
	}

	public void setOkMaxTime(long okMaxTime) {
		this.okMaxTime = okMaxTime;
	}

	public ZeroBasedMap<Integer, Long> getStatusAggregate() {
		return statusAggregate;
	}
	
	public ZeroBasedMap<String, Long> getUriOkCount() {
		return uriOkCount;
	}

	public void setUriOkCount(ZeroBasedMap<String, Long> uriOkCount) {
		this.uriOkCount = uriOkCount;
	}

	public ZeroBasedMap<String, Long> getUriOkTotalTime() {
		return uriOkTotalTime;
	}

	public void setUriOkTotalTime(ZeroBasedMap<String, Long> uriOkTotalTime) {
		this.uriOkTotalTime = uriOkTotalTime;
	}

	public Map<String, Long> getUriOkMinTime() {
		return uriOkMinTime;
	}

	public void setUriOkMinTime(ZeroBasedMap<String, Long> uriOkMinTime) {
		this.uriOkMinTime = uriOkMinTime;
	}

	public Map<String, Long> getUriOkMaxTime() {
		return uriOkMaxTime;
	}

	public void setUriOkMaxTime(ZeroBasedMap<String, Long> uriOkMaxTime) {
		this.uriOkMaxTime = uriOkMaxTime;
	}

	public Map<String, ZeroBasedMap<Integer, Long>> getUriStatusAggregate() {
		return uriStatusAggregate;
	}

	public void setUriTotalRequest(ZeroBasedMap<String, Long> uriTotalRequest) {
		this.uriTotalRequest = uriTotalRequest;
	}

	public ZeroBasedMap<String, Long> getUriTotalRequest() {
		return uriTotalRequest;
	}

	@Override
	public String toString() {
		return "Client [client=" + client + ", errorConnect=" + errorConnect
				+ ", errorSocketTimeout=" + errorSocketTimeout
				+ ", errorUnknown=" + errorUnknown + ", host=" + host
				+ ", okCount=" + okCount + ", okMaxTime=" + okMaxTime
				+ ", okMinTime=" + okMinTime + ", okTotalTime=" + okTotalTime
				+ ", repeatCount=" + repeatCount + ", requests=" + requests
				+ ", statusAggregate=" + statusAggregate
				+ ", totalRequestCount=" + totalRequestCount + ", uriOkCount="
				+ uriOkCount + ", uriOkMaxTime=" + uriOkMaxTime
				+ ", uriOkMinTime=" + uriOkMinTime + ", uriOkTotalTime="
				+ uriOkTotalTime + ", uriStatusAggregate=" + uriStatusAggregate
				+ "]";
	}

}
