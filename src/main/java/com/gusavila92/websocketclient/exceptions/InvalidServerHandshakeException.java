package com.gusavila92.websocketclient.exceptions;

public class InvalidServerHandshakeException extends RuntimeException {
	public InvalidServerHandshakeException(String message) {
		super(message);
	}
}
