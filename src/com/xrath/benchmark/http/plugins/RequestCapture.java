package com.xrath.benchmark.http.plugins;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * It can be subclass, then can be improved in saving each request via override 
 * 'storeRequest' method.
 * 
 * @author rath
 *
 */
public class RequestCapture {
	
	private File outputDirectory = new File(".");
	
	public RequestCapture() {
		
	}
	
	
	@SuppressWarnings("unchecked")
	public void capture(HttpServletRequest req) {
		Map<String, Object> toSend = new HashMap<String, Object>();
		
		try {
			String addr = req.getHeader("X-Real-IP");
			if( addr==null )
				addr = req.getRemoteAddr();
			String method = req.getMethod();
			Map params = req.getParameterMap();
			String uri = req.getRequestURI();
			
			{
				Map<String, String> headers = new HashMap<String, String>();
				for(Enumeration headerNames = req.getHeaderNames(); headerNames.hasMoreElements(); ) {
					String name = (String)headerNames.nextElement();
					headers.put(name, req.getHeader(name));
				}
				toSend.put("HEADERS", headers);
			}
			
			Map<String, Object> paramTo = new HashMap<String, Object>();
			for(Object paramName : params.keySet()) { 
				String k = (String)paramName;
				String[] v = ((String[])params.get(k));
				if( v.length==1 ) 
					paramTo.put(k, v[0]);
				else
					paramTo.put(k, v);
			}
			
			toSend.put("REMOTE_ADDR", addr);
			toSend.put("METHOD", method);
			toSend.put("PARAMETERS", paramTo);
			toSend.put("REQUEST_URI", uri);
			toSend.put("TIMESTAMP", System.currentTimeMillis());
			
			storeRequest(toSend);
			
		}
		catch( Throwable e ) { 
			System.err.println(e);
		}
	}

	protected void storeRequest(Map<String, Object> toSend) throws IOException {
		File f = new File(outputDirectory, "request-" + Thread.currentThread().getId() + ".log");
		OutputStream fos = null; 
		try {
			fos = new BufferedOutputStream(new FileOutputStream(f, true));
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(toSend);
			oos.flush();
			fos.flush();
		} finally {
			if( fos!=null )
				fos.close();
		}		
	}


	public void setOutputDirectory(File outputDirectory) {
		this.outputDirectory = outputDirectory;
	}


	public File getOutputDirectory() {
		return outputDirectory;
	}
}