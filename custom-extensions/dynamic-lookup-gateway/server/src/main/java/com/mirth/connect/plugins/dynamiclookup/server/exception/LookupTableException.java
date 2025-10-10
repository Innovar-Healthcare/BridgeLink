/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 *
 */

package com.mirth.connect.plugins.dynamiclookup.server.exception;

public class LookupTableException extends Exception {
	public LookupTableException(String message) {
		super(message);
	}

	public LookupTableException(String message, Throwable cause) {
		super(message, cause);
	}

	public LookupTableException(Throwable cause) {
		super(cause);
	}
}
