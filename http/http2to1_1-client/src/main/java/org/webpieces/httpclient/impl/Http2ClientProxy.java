package org.webpieces.httpclient.impl;

import javax.net.ssl.SSLEngine;

import org.webpieces.http2client.api.Http2Client;
import org.webpieces.http2client.api.Http2Socket;
import org.webpieces.httpclient.api.HttpClient;
import org.webpieces.httpclient.api.HttpSocket;

public class Http2ClientProxy implements Http2Client {

	private HttpClient client1_1;

	public Http2ClientProxy(HttpClient client1_1) {
		this.client1_1 = client1_1;
	}

	@Override
	public Http2Socket createHttpSocket(String idForLogging) {
		HttpSocket socket1_1 = client1_1.createHttpSocket(idForLogging);
		return new Http2SocketImpl(socket1_1);
	}

	@Override
	public Http2Socket createHttpsSocket(String idForLogging, SSLEngine factory) {
		HttpSocket socket1_1 = client1_1.createHttpSocket(idForLogging);
		return new Http2SocketImpl(socket1_1);
	}

}
