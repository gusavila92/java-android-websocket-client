package dev.gustavoavila.websocketclient.exceptions;

/**
 * Exception which indicates that a received opcode is unknown
 * 
 * @author Gustavo Avila
 *
 */
public class UnknownOpcodeException extends RuntimeException {
	public UnknownOpcodeException(String message) {
		super(message);
	}
}
