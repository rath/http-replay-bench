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

public class Reporter {
	private List<Client> clients;
	
	private int totalRequestCount = 0;
	private int errorConnect = 0;
	private int errorSocketTimeout = 0;
	private int errorUnknown = 0;
	
	private int okCount = 0;
	private long okTotalTime = 0L;
	private long okMinTime = Long.MAX_VALUE;
	private long okMaxTime = Long.MIN_VALUE;

	private Map<Integer, Integer> agg = new HashMap<Integer, Integer>();
	
	private DecimalFormat fmtNum = new DecimalFormat("#,###");
	private DecimalFormat fmtReal = new DecimalFormat("###.00");
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
			
			Map<Integer, Integer> a = c.getStatusAggregate();
			for(Integer code : a.keySet()) {
				if( !agg.containsKey(code) ) {
					agg.put(code, 0);
				}
				agg.put(code, agg.get(code)+a.get(code));
			}
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
		out.printf ("%-14s %7s %7s %7s%n", "", "min", "avg", "max");
		if( okCount==0 ) 
			out.printf ("%-14s %7s %7s %7s%n", "Response time", "N/A", "N/A", "N/A");
		else
			out.printf ("%-14s %5dms %5dms %5dms%n", "Response time", okMinTime, okTotalTime/okCount, okMaxTime);
		out.println();
		out.println("* Response codes");
		
		List<Integer> codes = new ArrayList<Integer>();
		codes.addAll(agg.keySet());
		Collections.sort(codes);
		for(Integer code : codes) {
			String label = codeLabel.get(code);
			if( label==null )
				label = "";
			out.printf("%-12s %3d: %20s  (%6s%%)%n", label, code, 
				fmtNum.format(agg.get(code)), 
				fmtReal.format((double)agg.get(code) / (double)totalRequestCount * 100.0));
				
		}
		out.println();
		out.println("* Errors");
		out.printf("%-18s %19d%n", "Connection refused", errorConnect);
		out.printf("%-18s %19d%n", "Request timeout", errorSocketTimeout);
		out.printf("%-18s %19d%n", "Unknown", errorUnknown);
	}
}
