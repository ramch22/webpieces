package org.webpieces.http2client.impl;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;
import org.webpieces.data.api.DataWrapperGeneratorFactory;
import org.webpieces.http2client.api.Http2SocketDataWriter;
import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.handlers.DataListener;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.webpieces.hpack.api.dto.Http2Headers;
import com.webpieces.http2engine.api.Http2ClientEngine;
import com.webpieces.http2engine.api.Http2ResponseListener;
import com.webpieces.http2engine.api.RequestWriter;
import com.webpieces.http2parser.api.dto.lib.PartialStream;

public class Layer1Incoming implements DataListener {

	private static final Logger log = LoggerFactory.getLogger(Layer1Incoming.class);
	private static final DataWrapperGenerator dataGen = DataWrapperGeneratorFactory.createDataWrapperGenerator();
	private Http2ClientEngine layer2;
	private AtomicInteger nextAvailableStreamId = new AtomicInteger(1);

	public Layer1Incoming(Http2ClientEngine layer2) {
		this.layer2 = layer2;
	}

	public CompletableFuture<Void> sendInitialFrames() {
		return layer2.sendInitializationToSocket();
	}
	
	public CompletableFuture<Void> sendPing() {
		return layer2.sendPing();
	}
	
	public CompletableFuture<Http2SocketDataWriter> sendRequest(Http2Headers request, Http2ResponseListener listener) {
		int streamId = getNextAvailableStreamId();
		request.setStreamId(streamId);

		return layer2.sendFrameToSocket(request, listener)
						.thenApply(c -> createWriter(request, c));
	}

	private Http2SocketDataWriter createWriter(Http2Headers request, RequestWriter requestWriter) {
		Http2SocketDataWriter writer = new Writer(request.getStreamId(), request.isEndOfStream(), requestWriter);
		return writer;
	}
	
	private class Writer implements Http2SocketDataWriter {
		private RequestWriter requestWriter;
		private int streamId;
		private boolean isEndOfStream;

		public Writer(int streamId, boolean isEndOfStream, RequestWriter requestWriter) {
			this.streamId = streamId;
			this.isEndOfStream = isEndOfStream;
			this.requestWriter = requestWriter;
		}

		@Override
		public CompletableFuture<Http2SocketDataWriter> sendData(PartialStream data) {
			if(isEndOfStream)
				throw new IllegalStateException("Client has already sent a PartialStream"
						+ " object with endOfStream=true so no more data can be sent");
			
			if(data.isEndOfStream())
				isEndOfStream = true;
			
			data.setStreamId(streamId);

			return requestWriter.sendMore(data).thenApply(c -> this);
		}
	}
	
	private int getNextAvailableStreamId() {
		return nextAvailableStreamId.getAndAdd(2);
	}

	@Override
	public void incomingData(Channel channel, ByteBuffer b) {
		log.info(channel+"incoming data. size="+b.remaining());
		DataWrapper data = dataGen.wrapByteBuffer(b);
		//log.info("data="+data.createStringFrom(0, data.getReadableSize(), StandardCharsets.UTF_8));
		layer2.parse(data);
	}

	@Override
	public void farEndClosed(Channel channel) {
		layer2.farEndClosed();
	}

	@Override
	public void failure(Channel channel, ByteBuffer data, Exception e) {
		log.warn("failure", e);
	}

	@Override
	public void applyBackPressure(Channel channel) {
		log.info("apply back pressure");
	}

	@Override
	public void releaseBackPressure(Channel channel) {
		log.info("apply back pressure");
	}

}