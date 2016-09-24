package com.webpieces.http2parser.api.dto;

import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.impl.ByteBufferDataWrapper;

import java.nio.ByteBuffer;

public class Http2WindowUpdate extends Http2Frame {
	public Http2FrameType getFrameType() {
		return Http2FrameType.WINDOW_UPDATE;
	}
	/* flags */
	public byte getFlagsByte() {
		return 0x0;
	}
	public void setFlags(byte flags) {}

	/* payload */
	//1bit reserved
	private int windowSizeIncrement; //31 bits
	protected DataWrapper getPayloadDataWrapper() {
		ByteBuffer payload = ByteBuffer.allocate(4).putInt(windowSizeIncrement);
		return new ByteBufferDataWrapper(payload);
	}
	
}