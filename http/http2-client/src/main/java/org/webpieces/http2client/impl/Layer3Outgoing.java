package org.webpieces.http2client.impl;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.webpieces.http2client.api.Http2Socket;
import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.channels.TCPChannel;
import org.webpieces.util.logging.Logger;
import org.webpieces.util.logging.LoggerFactory;

import com.webpieces.http2engine.api.client.ClientEngineListener;
import com.webpieces.http2parser.api.dto.error.Http2Exception;
import com.webpieces.http2parser.api.dto.lib.Http2Frame;

public class Layer3Outgoing implements ClientEngineListener {
	
	private static final Logger log = LoggerFactory.getLogger(Layer3Outgoing.class);
	private TCPChannel channel;
	
	private Http2Socket socket;

	public Layer3Outgoing(TCPChannel channel, Http2Socket socket) {
		this.channel = channel;
		this.socket = socket;
	}

	@Override
	public CompletableFuture<Void> sendToSocket(ByteBuffer data) {
		log.info("writing out data to socket size="+data.remaining());
		return channel.write(data)
						.thenApply(c -> null);
	}

	public void sendPreface(ByteBuffer buf) {
		channel.write(buf);
	}

	public CompletableFuture<Channel> connect(InetSocketAddress addr, Layer1Incoming incoming) {
		return channel.connect(addr, incoming);
	}

	public CompletableFuture<Channel> close() {
		return channel.close();
	}

	@Override
	public void sendControlFrameToClient(Http2Frame lowLevelFrame) {
	}

	@Override
	public void engineClosedByFarEnd() {
	}
	
	@Override
	public void closeSocket(Http2Exception reason) {
		channel.close();
	}
}
