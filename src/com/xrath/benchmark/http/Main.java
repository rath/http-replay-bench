package com.xrath.benchmark.http;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Main {

	/**
	 * @param args
	 * @throws ParseException 
	 * @throws IOException 
	 */
	public static void main(String[] args) throws ParseException, IOException {
	// java -jar rb.jar -d samples -c 100 -n 20000 http://anipang-j.sundaytoz.com
		Options options = new Options();
		options.addOption("d", "dir", true, "A directory pathname that includes captured http-requests in file formats.");
		options.addOption("c", "concurrency", true, "concurrency. number of multiple requests to replay at a time. Default is one request at a time. The default implementation is built with n-thread.");
		options.addOption("n", "repeat", true, "Number of requests to replay for benchmarking session. this number is belong to each thread to replay http-requests.");
		
		HelpFormatter formatter = new HelpFormatter();
		String usage = "java -jar rb.jar -d samples -c 10 -n unlimited http://test.org";
		
		CommandLineParser parser = new GnuParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args, true);
		} catch( MissingArgumentException e ) {
			formatter.printHelp(usage, options);
			System.err.println( e.getMessage() );
			System.exit(1);
		}
		
		File dir = new File(".");
		if( cmd.hasOption("d") ) {
			dir = new File(cmd.getOptionValue("d"));
			if(!dir.exists()) {
				System.err.println( "A directory you specified " + dir.getName() + " is not found.");
				System.exit(1);
			}
		}
		else {
			formatter.printHelp(usage, options);
			System.err.println( "option 'dir' must be given.");
			System.exit(1);
		}
		
		int concurrency = 20;
		if( cmd.hasOption("c") ) { 
			concurrency = Integer.parseInt(cmd.getOptionValue("c"));
		}
		else {
			formatter.printHelp(usage, options);
			System.err.println( "option 'concurrency' must be given.");
			System.exit(1);
		}
		
		int repeatPerThread = 10000;
		if( cmd.hasOption("n") ) { 
			String nValue = cmd.getOptionValue("n");
			if( nValue.equals("unlimited") ) 
				nValue = "-1";
			repeatPerThread = Integer.parseInt(nValue);
		}
		else {
			repeatPerThread = -1;
		}
		
		RequestLoader rl = new RequestLoader();
		rl.setDirectory(dir);
		List<Map<String, Object>> requests = rl.load(); 
		
		startTime = System.currentTimeMillis();
		
		String host = cmd.getArgs()[0];
		final List<Client> clients = new ArrayList<Client>();
		for(int i=0; i<concurrency; i++) {
			Client client = new Client();
			client.setHost(host);
			client.setRepeatCount(repeatPerThread);
			client.setRequests(requests);
			client.start();
			clients.add(client);
		}
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable(){
			@Override
			public void run() {
				printReport(clients);
			}
		}));
		
		for(Client c : clients) {
			try {
				c.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	static long startTime;

	protected static void printReport(List<Client> clients) {
		
		Reporter r = new Reporter();
		r.setClients(clients);
		r.merge();
		r.print();
	}

}
