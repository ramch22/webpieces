package com.webpieces.http2engine.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.webpieces.javasm.api.Memento;

import com.webpieces.http2engine.api.Http2Payload;
import com.webpieces.http2parser.api.ParseException;
import com.webpieces.http2parser.api.dto.Http2ErrorCode;

public class Level3StreamInitialization {

	private Level4ClientStateMachine clientSm;

	private Map<Integer, Stream> streamIdToStream = new HashMap<>();

	public Level3StreamInitialization(Level4ClientStateMachine clientSm) {
		this.clientSm = clientSm;
	}

	public synchronized CompletableFuture<Void> outgoingFrame(Http2Payload frame) {
		int streamId = frame.getStreamId();

		Stream stream = streamIdToStream.get(streamId);
		if (stream == null) { // idle state
			stream = createStream(streamId);
		}

		Memento currentState = stream.getCurrentState();
		return clientSm.fireToSocket(currentState, frame);
	}

	private Stream createStream(int streamId) {
		Memento initialState = clientSm.createStateMachine("stream" + streamId);
		Stream stream = new Stream(initialState);
		streamIdToStream.put(streamId, stream);
		return stream;
	}
	
	public void sendPayloadToClient(Http2Payload frame) {
		int streamId = frame.getStreamId();
		if (streamId % 2 == 1)
			throw new ParseException(Http2ErrorCode.PROTOCOL_ERROR, streamId,
					"Server sent us bad frame id per http/2 spec as in it was an odd id=" + streamId);

		Stream stream = streamIdToStream.get(streamId);
		if (stream == null) {
			createStream(streamId);
		}

		clientSm.fireToClient(frame);
	}
}