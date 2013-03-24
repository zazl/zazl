/*
    Copyright (c) 2004-2010, The Dojo Foundation All Rights Reserved.
    Available via Academic Free License >= 2.1 OR the modified BSD license.
    see: http://dojotoolkit.org/license for details
*/
package org.dojotoolkit.zazl.jetty.internal;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dojotoolkit.optimizer.JSOptimizer;
import org.dojotoolkit.server.util.resource.ResourceLoader;
import org.dojotoolkit.server.util.rhino.RhinoClassLoader;
import org.dojotoolkit.zazl.servlet.ZazlHandler;
import org.eclipse.jetty.server.AbstractHttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class JettyZazlHandler extends AbstractHandler {
	protected ZazlHandler zazlHandler = null;
	protected JSOptimizer jsOptimizer = null;

	public JettyZazlHandler() {
		zazlHandler = new ZazlHandler();
	}
	
	protected void initialize(File root, ResourceLoader resourceLoader, RhinoClassLoader rhinoClassLoader, JSOptimizer jsOptimizer) {
		this.jsOptimizer = jsOptimizer;
		try {
			zazlHandler.initialize(Boolean.valueOf(System.getProperty("V8", "false")), 
					               Boolean.valueOf(System.getProperty("DEBUG", "false")), 
					               new File(root, "URLMap.json").toURI().toURL(), 
					               new File(root, "CallbackConfig.json").toURI().toURL(), 
					               resourceLoader, 
					               rhinoClassLoader);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}
	
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    	request.setAttribute("org.dojotoolkit.optimizer.JSOptimizer", jsOptimizer);
    	boolean handled = zazlHandler.handle(request, response);
    	if (handled) {
			Request jettyRequest = (request instanceof Request) ? (Request)request:AbstractHttpConnection.getCurrentConnection().getRequest();
			jettyRequest.setHandled(true);
    	}
	}
}
