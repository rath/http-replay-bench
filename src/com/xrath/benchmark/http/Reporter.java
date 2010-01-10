package com.xrath.benchmark.http;

import static java.lang.System.out;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.xrath.benchmark.http.util.ZeroBasedMap;

public class Reporter {
	private List<Client> clients;
	
	private int totalRequestCount = 0;
	private int errorConnect = 0;
	private int errorSocketTimeout = 0;
	private int errorUnknown = 0;
	
	// Global variables
	private int okCount = 0;
	private long okTotalTime = 0L;
	private long okMinTime = Long.MAX_VALUE;
	private long okMaxTime = Long.MIN_VALUE;

	private ZeroBasedMap<Integer, Long> agg = new ZeroBasedMap<Integer, Long>();
	
	// URI base variables
	private ZeroBasedMap<String, Long> uriTotalRequest = new ZeroBasedMap<String, Long>();
	private ZeroBasedMap<String, Long> uriOkCount = new ZeroBasedMap<String, Long>();
	private ZeroBasedMap<String, Long> uriOkTotalTime = new ZeroBasedMap<String, Long>();
	private ZeroBasedMap<String, Long> uriOkMinTime = new ZeroBasedMap<String, Long>();
	private ZeroBasedMap<String, Long> uriOkMaxTime = new ZeroBasedMap<String, Long>();
	
	private Map<String, ZeroBasedMap<Integer, Long>> uriStatusAggregate = new HashMap<String, ZeroBasedMap<Integer, Long>>();
	
	private DecimalFormat fmtNum = new DecimalFormat("#,###");
	private DecimalFormat fmtReal = new DecimalFormat("##0.00");
	private SimpleDateFormat fmtTime = new SimpleDateFormat("HH'h' mm'm' ss's'");
	
	private Map<Integer, String> codeLabel = new HashMap<Integer, String>();
	
	public Reporter() {
		fmtTime.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		codeLabel.put(200, "OK");
		codeLabel.put(302, "Moved");
		codeLabel.put(403, "Forbidden");
		codeLabel.put(404, "Not found");
		codeLabel.put(500, "Server error");
		codeLabel.put(502, "Bad gateway");
		codeLabel.put(503, "Down");
		
		agg.put(200, 0L);
		agg.put(302, 0L);
		agg.put(403, 0L);
		agg.put(404, 0L);
		agg.put(500, 0L);
		agg.put(502, 0L);
		agg.put(503, 0L);
	}

	public void setClients(List<Client> clients) {
		this.clients = clients;
	}

	public List<Client> getClients() {
		return clients;
	}
	
	public void merge() {
		for(Client c : clients) { 
			totalRequestCount += c.getTotalRequestCount();
			errorConnect += c.getErrorConnect();
			errorSocketTimeout += c.getErrorSocketTimeout();
			errorUnknown += c.getErrorUnknown();
			
			okCount += c.getOkCount();
			okTotalTime += c.getOkTotalTime();
			okMinTime = Math.min(okMinTime, c.getOkMinTime());
			okMaxTime = Math.max(okMaxTime, c.getOkMaxTime());
			
			ZeroBasedMap<Integer, Long> a = c.getStatusAggregate();
			for(Integer code : a.keySet()) {
				agg.add(code, a.get(code));
			}
			
			// URI based 
			uriOkCount.addAll(c.getUriOkCount());
			uriOkTotalTime.addAll(c.getUriOkTotalTime());
			
			Map<String, Long> maxTimes = c.getUriOkMaxTime();
			for(String uri : maxTimes.keySet()) {
				if( !uriOkMinTime.containsKey(uri) )
					uriOkMinTime.put(uri, Long.MAX_VALUE);
				uriOkMaxTime.put(uri, Math.max(maxTimes.get(uri), uriOkMaxTime.get(uri)));
			}
			Map<String, Long> minTimes = c.getUriOkMinTime();
			for(String uri : minTimes.keySet()) {
				uriOkMinTime.put(uri, Math.min(minTimes.get(uri), uriOkMinTime.get(uri)));
			}
			
			Map<String, ZeroBasedMap<Integer, Long>> uriA = c.getUriStatusAggregate();
			for(String uri : uriA.keySet()) {
				if( !uriStatusAggregate.containsKey(uri) ) 
					uriStatusAggregate.put(uri, new ZeroBasedMap<Integer, Long>());
				uriStatusAggregate.get(uri).addAll(uriA.get(uri));
			}
			
			uriTotalRequest.addAll(c.getUriTotalRequest());
		}
	}
	
	public void print() {
		
		out.println();
		out.println();
		out.println("* Number of sent requests");
		out.println(fmtNum.format(totalRequestCount));
		out.println();
		out.println("* Total time to consume");
		out.println( fmtTime.format(new Date(System.currentTimeMillis()-Main.startTime)) );
		out.println();
		out.printf ("%-30s %7s %7s %7s%n", "", "min", "avg", "max");
		
		if( okCount==0 ) 
			out.printf ("%-30s %7s %7s %7s%n", "Response time", "N/A", "N/A", "N/A");
		else
			out.printf ("%-30s %5dms %5dms %5dms%n", "Response time", okMinTime, okTotalTime/okCount, okMaxTime);
		for(String uri : uriOkTotalTime.keySet()) {
			out.printf ("%-30s %5dms %5dms %5dms%n", uri,
					uriOkMinTime.get(uri), 
					uriOkTotalTime.get(uri) / uriOkCount.get(uri), 
					uriOkMaxTime.get(uri));
		}
		
		out.println();
		out.println("* Response codes");
		
		List<Integer> codes = new ArrayList<Integer>();
		codes.addAll(agg.keySet());
		Collections.sort(codes);
		for(Integer code : codes) {
			String label = codeLabel.get(code);
			if( label==null )
				label = "";
			out.printf("%-20s %3d: %20s  (%6s%%)%n", label, code, 
				fmtNum.format(agg.get(code)), 
				fmtReal.format((double)agg.get(code) / (double)totalRequestCount * 100.0));
			
			for(String uri : uriStatusAggregate.keySet()) {
				Map<Integer, Long> sa = uriStatusAggregate.get(uri);
				if( sa.get(code)==0 ) 
					continue;
				out.printf("   %-26s %16s  (%6s%%)%n", uri, 
						fmtNum.format(sa.get(code)), 
						fmtReal.format((double)sa.get(code) / (double)uriTotalRequest.get(uri) * 100.0));
			}
		}
		out.println();
		out.println("* Errors");
		out.printf("%-18s %27d%n", "Connection refused", errorConnect);
		out.printf("%-18s %27d%n", "Request timeout", errorSocketTimeout);
		out.printf("%-18s %27d%n", "Unknown", errorUnknown);
	}
}
