package com.gusavila92.websocketclient.model;

public class Payload {
	
	private int opcode;
	
	private byte[] data;
	
	public Payload(int opcode, byte[] data) {
		this.opcode = opcode;
		this.data = data;
	}

	public int getOpcode() {
		return opcode;
	}

	public byte[] getData() {
		return data;
	}
	
}
