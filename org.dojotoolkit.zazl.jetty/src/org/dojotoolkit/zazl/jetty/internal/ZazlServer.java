/*
    Copyright (c) 2004-2010, The Dojo Foundation All Rights Reserved.
    Available via Academic Free License >= 2.1 OR the modified BSD license.
    see: http://dojotoolkit.org/license for details
*/
package org.dojotoolkit.zazl.jetty.internal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dojotoolkit.compressor.JSCompressorFactory;
import org.dojotoolkit.compressor.JSCompressorFactoryImpl;
import org.dojotoolkit.json.JSONParser;
import org.dojotoolkit.optimizer.JSOptimizerFactory;
import org.dojotoolkit.optimizer.rhino.RhinoJSOptimizerFactory;
import org.dojotoolkit.optimizer.v8.V8JSOptimizerFactory;
import org.dojotoolkit.server.util.resource.ResourceLoader;
import org.dojotoolkit.server.util.rhino.RhinoClassLoader;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.FileResource;
import org.eclipse.jetty.util.resource.Resource;

public class ZazlServer {
	static {
		org.eclipse.jetty.util.log.Log.setLog(new ZazlServer.NullLogger());
	}
	
	private Server server = new Server();
	private File root = null;
	private ResourceHandler[] resourceHandlers = null;
	private boolean started = false;
	
	public ZazlServer(File root, ResourceHandler[] resourceHandlers) {
		this.root = root;
		this.resourceHandlers = resourceHandlers;
	}
	
	public synchronized void start(JettyZazlHandler zazlHandler, ResourceLoader resourceLoader) throws Exception {
		if (!started) {
			started = true;
			if (!root.exists()) {
				throw new FileNotFoundException(root.getPath());
			}
			server = new Server();
			Connector connector = new SocketConnector();
			connector.setPort(8080);
			server.setConnectors(new Connector[]{connector});
			             
			HandlerList handlerList = new HandlerList();
			RhinoClassLoader rhinoClassLoader = new RhinoClassLoader(resourceLoader);

			boolean useV8 = Boolean.valueOf(System.getProperty("V8", "false"));
			boolean javaChecksum = Boolean.valueOf(System.getProperty("javaChecksum", "false"));
			JSOptimizerFactory jsOptimizerFactory = null;
			if (useV8) {
				jsOptimizerFactory = new V8JSOptimizerFactory();
			} else {
				jsOptimizerFactory = new RhinoJSOptimizerFactory();
			}
			String compressJS = System.getProperty("compressJS");

			JSCompressorFactory jsCompressorFactory = null;
			if (compressJS != null && compressJS.equalsIgnoreCase("true")) {
				jsCompressorFactory = new JSCompressorFactoryImpl();
			}
			JSContentHandler jsContentHandler = new JSContentHandler(resourceLoader, jsOptimizerFactory, rhinoClassLoader, jsCompressorFactory); 
			zazlHandler.initialize(root, resourceLoader, rhinoClassLoader, jsContentHandler.getJSOptimizer()); 
			ResourceHandler rootHandler = new ResourceHandler();
			rootHandler.setBaseResource(new FileResource(new URL("file:"+root.getCanonicalPath())));
			handlerList.addHandler(zazlHandler);
			handlerList.addHandler(jsContentHandler);
			handlerList.addHandler(rootHandler);
			for (ResourceHandler resourceHandler : resourceHandlers) {
				handlerList.addHandler(resourceHandler);
			}
			server.setHandler(handlerList);
			server.start();
			System.out.println("Browse demos at \"http://localhost:8080/index.html\"");
		}
	}
	
	public synchronized void stop() throws Exception {
		if (started) {
			started = false;
			server.stop();
		}
	}
	
	private static URL findJSJar(File dir, String prefix, String jarSuffix) {
		URL jarURL = null;
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.getName().startsWith(prefix)) {
				try {
					jarURL = new URL("jar:file:"+file.getCanonicalPath()+jarSuffix);
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			}
		}
		return jarURL;
	}
	
	public static void main(String[] args) {
		if (args.length > 0) {
			File root = new File(args[0]);
			try {
				ResourceHandler[] resourceHandlers = loadResourceHandlers();
				ResourceLoader resourceLoader = new JettyResourceLoader(root, resourceHandlers);
				ZazlServer dtlServer = new ZazlServer(root, resourceHandlers);
				JettyZazlHandler zazlHandler = new JettyZazlHandler();
				dtlServer.start(zazlHandler, resourceLoader);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		else {
			System.out.println("zazlserver <resource directory>");
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static ResourceHandler[] loadResourceHandlers() {
		File currentDir = new File(".");
		File libraryDir = new File(currentDir, "library");
		ResourceHandler[] resourceHandlers = null;
		URL resourceHandlersJsonURL = ZazlServer.class.getClassLoader().getResource("resourcehandlers.json");
		if (resourceHandlersJsonURL != null) {
			InputStream is = null;
			Reader r = null;
			try {
				is = resourceHandlersJsonURL.openStream();
				r = new BufferedReader(new InputStreamReader(is));
				List resourceHandlersJson = (List)JSONParser.parse(r);
				List<ResourceHandler> resourceHandlerList = new ArrayList<ResourceHandler>();
				for (Iterator itr = resourceHandlersJson.iterator(); itr.hasNext();) {
					Map<String, Object> resourceHandlerJson = (Map<String, Object>)itr.next();
					String jarnamePrefix = (String)resourceHandlerJson.get("jarnamePrefix");
					String path = (String)resourceHandlerJson.get("path");
					URL resourceHandlerJarURL = findJSJar(currentDir, jarnamePrefix, '!'+path);
					if (resourceHandlerJarURL == null) {
						resourceHandlerJarURL = findJSJar(libraryDir, jarnamePrefix, '!'+path);
					}
					if (resourceHandlerJarURL != null) {
						ResourceHandler resourceHandler = new ResourceHandler();
						resourceHandler.setBaseResource(Resource.newResource(resourceHandlerJarURL));
						resourceHandlerList.add(resourceHandler);
					} else {
						System.out.println("Unable to find resource handler for ["+jarnamePrefix+'!'+path+"]");
					}
				}
				resourceHandlers = new ResourceHandler[resourceHandlerList.size()];
				resourceHandlers = resourceHandlerList.toArray(resourceHandlers);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (is != null) { try { is.close(); } catch (IOException e) {}}
			}
		} else {
			System.out.println("Unable to find resourcehandlers.json");
		}
		return resourceHandlers;
	}
	
	public static class NullLogger implements Logger {
		public void debug(String arg0, Throwable arg1) {
		}

		public void debug(String arg0, Object arg1, Object arg2) {
		}

		public Logger getLogger(String arg0) {
			return null;
		}

		public void info(String arg0, Object arg1, Object arg2) {
		}

		public boolean isDebugEnabled() {
			return false;
		}

		public void setDebugEnabled(boolean arg0) {
		}

		public void warn(String arg0, Throwable arg1) {
		}

		public void warn(String arg0, Object arg1, Object arg2) {
		}

		public String getName() {
			return null;
		}

		public void warn(String msg, Object... args) {
		}

		public void warn(Throwable thrown) {
		}

		public void info(String msg, Object... args) {
		}

		public void info(Throwable thrown) {
		}

		public void info(String msg, Throwable thrown) {
		}

		public void debug(String msg, Object... args) {
		}

		public void debug(Throwable thrown) {
		}

		public void ignore(Throwable ignored) {
		}
	}
	
}
