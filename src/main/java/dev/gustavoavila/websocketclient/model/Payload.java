package dev.gustavoavila.websocketclient.model;

/**
 * A payload that will be send through the WebSocket connection
 * 
 * @author Gustavo Avila
 *
 */
public class Payload {
	/**
	 * Opcode of the payload
	 */
	private int opcode;

	/**
	 * Data included into the payload
	 */
	private byte[] data;

	/**
	 * This is true if a close frame was previously received and this payload represents the echo
	 */
	private boolean isCloseEcho;

	/**
	 * Initializes the variables
	 * 
	 * @param opcode
	 * @param data
	 */
	public Payload(int opcode, byte[] data, boolean isCloseEcho) {
		this.opcode = opcode;
		this.data = data;
		this.isCloseEcho = isCloseEcho;
	}

	/**
	 * Returns the opcode
	 * 
	 * @return
	 */
	public int getOpcode() {
		return opcode;
	}

	/**
	 * Returns the data
	 * 
	 * @return
	 */
	public byte[] getData() {
		return data;
	}

	public boolean isCloseEcho() {
		return isCloseEcho;
	}
}
