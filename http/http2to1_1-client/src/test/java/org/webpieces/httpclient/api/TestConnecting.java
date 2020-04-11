package org.webpieces.httpclient.api;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.webpieces.data.api.BufferPool;
import org.webpieces.data.api.TwoPools;
import org.webpieces.http2client.api.Http2Client;
import org.webpieces.http2client.api.Http2Socket;
import org.webpieces.httpclient.api.mocks.MockChannel;
import org.webpieces.httpclient.api.mocks.MockChannelMgr;
import org.webpieces.httpclientx.api.Http2to11ClientFactory;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class TestConnecting {

	private MockChannelMgr mockChannelMgr = new MockChannelMgr();
	private MockChannel mockChannel = new MockChannel();
	private Http2Client httpClient;

	@Before
	public void setup() {
		BufferPool pool = new TwoPools("pl", new SimpleMeterRegistry());
		httpClient = Http2to11ClientFactory.createHttpClient("myClient2", mockChannelMgr, new SimpleMeterRegistry(), pool);
	}

	@Test
	public void testConnecting() {
		mockChannelMgr.addTCPChannelToReturn(mockChannel);
		Http2Socket socket = httpClient.createHttpSocket();

		CompletableFuture<Void> future1 = new CompletableFuture<Void>();
		mockChannel.setConnectFuture(future1);
		CompletableFuture<Void> future = socket.connect(new InetSocketAddress(8080));
		
		Assert.assertFalse(future.isDone());
		
		future1.complete(null);
		
		Assert.assertTrue(future.isDone());
		
	}

}
