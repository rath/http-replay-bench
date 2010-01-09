package com.xrath.benchmark.http.plugins;

import java.io.File;
import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * <filter>
 *   <filter-name>request-capture</filter-name>
 *   <filter-class>com.xrath.benchmark.http.plugins.RequestCaptureFilter</filter-class>
 *   <init-param>
 *     <param-name>output-directory</param-name>
 *     <param-value>/tmp</param-value>
 *   </init-param>
 * </filter>
 * 
 * <filter-mapping>
 *   <filter-name>request-capture</filter-name>
 *   <url-pattern>/*</url-pattern>
 * </filter-mapping>
 * 
 * @author rath
 *
 */
public class RequestCaptureFilter implements javax.servlet.Filter {

	private RequestCapture rc;
	
	@Override
	public void init(FilterConfig config) throws ServletException {
		String dir = config.getInitParameter("output-directory");
		
		rc = new RequestCapture();
		rc.setOutputDirectory(new File(dir));
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res,
			FilterChain chain) throws ServletException, IOException {
		rc.capture((HttpServletRequest) req);
		chain.doFilter(req, res);
	}

	@Override
	public void destroy() {
		
	}	

}
