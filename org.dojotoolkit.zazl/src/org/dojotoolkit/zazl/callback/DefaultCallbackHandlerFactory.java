/*
    Copyright (c) 2004-2010, The Dojo Foundation All Rights Reserved.
    Available via Academic Free License >= 2.1 OR the modified BSD license.
    see: http://dojotoolkit.org/license for details
*/
package org.dojotoolkit.zazl.callback;

public class DefaultCallbackHandlerFactory implements ICallbackHandlerFactory {
	public ICallbackHandler createCallbackHandler() {
		return new DefaultCallbackHandler();
	}
}
