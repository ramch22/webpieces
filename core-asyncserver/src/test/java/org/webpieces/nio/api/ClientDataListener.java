package org.webpieces.nio.api;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.handlers.DataListener;

import com.webpieces.data.api.BufferPool;

final class ClientDataListener implements DataListener {
	private static final Logger log = LoggerFactory.getLogger(ClientDataListener.class);
	
	private BufferPool pool2;
	private BytesRecorder recorder;
	
	public ClientDataListener(BufferPool pool2, BytesRecorder recorder) {
		this.pool2 = pool2;
		this.recorder = recorder;
	}
	
	@Override
	public void incomingData(Channel channel, ByteBuffer b) {
		recorder.recordBytes(b.remaining());
		
		b.position(b.limit());
		pool2.releaseBuffer(b);
	}

	@Override
	public void farEndClosed(Channel channel) {
		log.info("far end closed");
	}

	@Override
	public void failure(Channel channel, ByteBuffer data, Exception e) {
		log.info("failure", e);
	}
	
	@Override
	public void applyBackPressure(Channel channel) {
		log.info("client unregistering for reads");
		channel.unregisterForReads();
	}

	@Override
	public void releaseBackPressure(Channel channel) {
		log.info("client registring for reads");
		channel.registerForReads();
	}
}