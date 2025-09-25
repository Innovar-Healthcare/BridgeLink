/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.messagetrends.shared.dto.response;

public class ErrorResponse {
	private String status;
	private String code;
	private String message;
	private String timestamp;

	public ErrorResponse() {
	}

	public ErrorResponse(String code, String message) {
		this.status = "error";
		this.code = code;
		this.message = message;
		this.timestamp = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).toString();
	}

	public String getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}
}
