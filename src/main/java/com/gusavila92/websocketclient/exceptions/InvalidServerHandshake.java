package com.gusavila92.websocketclient.exceptions;

public class InvalidServerHandshake extends RuntimeException {
	public InvalidServerHandshake(String message) {
		super(message);
	}
}
