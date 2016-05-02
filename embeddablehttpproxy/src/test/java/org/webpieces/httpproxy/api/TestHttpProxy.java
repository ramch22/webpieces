package org.webpieces.httpproxy.api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.Before;
import org.junit.Test;
import org.webpieces.asyncserver.api.AsyncServerManager;
import org.webpieces.nio.api.handlers.DataListener;
import org.webpieces.util.threading.DirectExecutor;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.webpieces.data.api.BufferCreationPool;
import com.webpieces.httpparser.api.HttpParser;
import com.webpieces.httpparser.api.HttpParserFactory;
import com.webpieces.httpparser.api.dto.HttpRequest;
import com.webpieces.httpparser.api.dto.HttpRequestLine;
import com.webpieces.httpparser.api.dto.HttpUri;
import com.webpieces.httpparser.api.dto.KnownHttpMethod;

public class TestHttpProxy {
	
	private HttpParser parser = HttpParserFactory.createParser(new BufferCreationPool());
	private MockAsyncServerManager mockChannelMgr = new MockAsyncServerManager();
	private MockTcpChannel mockTcpChannel = new MockTcpChannel();
	private DataListener dataListener;
	
	@Before
	public void setUp() throws IOException {
		HttpProxy proxy = HttpProxyFactory.createHttpProxy("myproxy", new TestModule() );
		proxy.start();

		List<DataListener> serverListeners = mockChannelMgr.getServerListeners();
		dataListener = serverListeners.get(0);
	}
	
	@Test
	public void testBasicProxy() throws IOException {
		HttpRequestLine requestLine = new HttpRequestLine();
		requestLine.setMethod(KnownHttpMethod.GET);
		requestLine.setUri(new HttpUri("http://www.deano.com"));
		HttpRequest req = new HttpRequest();
		req.setRequestLine(requestLine);
		byte[] array = parser.marshalToBytes(req);
		
		ByteBuffer buffer = ByteBuffer.wrap(array);
		dataListener.incomingData(mockTcpChannel, buffer);
	}
	
	private class TestModule implements Module {
		@Override
		public void configure(Binder binder) {
			binder.bind(AsyncServerManager.class).toInstance(mockChannelMgr);
			binder.bind(Executor.class).toInstance(new DirectExecutor());
		}
	}
}
