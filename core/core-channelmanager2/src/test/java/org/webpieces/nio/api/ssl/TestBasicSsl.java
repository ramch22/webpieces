package org.webpieces.nio.api.ssl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLEngine;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.webpieces.data.api.BufferCreationPool;
import org.webpieces.data.api.BufferPool;
import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.DataWrapperGenerator;
import org.webpieces.data.api.DataWrapperGeneratorFactory;
import org.webpieces.nio.api.BackpressureConfig;
import org.webpieces.nio.api.ChannelManager;
import org.webpieces.nio.api.ChannelManagerFactory;
import org.webpieces.nio.api.channels.TCPChannel;
import org.webpieces.nio.api.mocks.MockClientSideJdkChannel;
import org.webpieces.nio.api.mocks.MockJdk;
import org.webpieces.nio.api.mocks.MockSslDataListener;
import org.webpieces.ssl.api.AsyncSSLFactory;
import org.webpieces.ssl.api.SSLParser;
import org.webpieces.ssl.api.dto.SslAction;
import org.webpieces.ssl.api.dto.SslActionEnum;
import org.webpieces.util.threading.DirectExecutor;

public class TestBasicSsl {

	private static final DataWrapperGenerator dataGen = DataWrapperGeneratorFactory.createDataWrapperGenerator();
	private MockSslDataListener mockClientDataListener = new MockSslDataListener();
	
	private MockClientSideJdkChannel mockChannel = new MockClientSideJdkChannel();
	private MockJdk mockJdk = new MockJdk(mockChannel);

	private SSLParser svrSslParser;

	private TCPChannel channel;

	private CompletableFuture<Void> connectFuture;

	@Before
	public void setup() throws GeneralSecurityException, IOException, InterruptedException, ExecutionException, TimeoutException {
		MockSSLEngineFactory sslFactory = new MockSSLEngineFactory();	
		BufferPool pool = new BufferCreationPool(false, 17000, 1000);
		SSLEngine clientSsl = sslFactory.createEngineForSocket();
		SSLEngine svrSsl = sslFactory.createEngineForServerSocket();
		svrSslParser = AsyncSSLFactory.create("svr", svrSsl, pool);

		ChannelManager chanMgr = createSvrChanMgr("server");
		
		channel = chanMgr.createTCPChannel("client", clientSsl);
		
		int port = 8443;
		
		mockChannel.setNumBytesToConsume(100000);
		mockChannel.addConnectReturnValue(true);
		mockJdk.setThread(Thread.currentThread()); //trick the selector into thinking we are on the selector thread
		connectFuture = channel.connect(new InetSocketAddress("localhost", port), mockClientDataListener);
		Assert.assertFalse(connectFuture.isDone()); //not connected until ssl handshake is complete

		SslAction result = parseIncoming();
		Assert.assertEquals(SslActionEnum.SEND_TO_SOCKET, result.getSslAction());
		
		mockChannel.forceDataRead(mockJdk, result.getEncryptedData());
	}
	
	//begin handshake results in ONE packet client -> server (server creates runnable, creating ONE
	//server creates runnable, runs it creating ONE packet server -> client
	//client creates runnable, runs it creating THREE packets client -> server
	//all 3 received, server creates TWO packets  client -> server (server is connected here)
	//client receives two packets as ONE packet here and is connected
	
	@Test
	public void testBasic() throws InterruptedException, ExecutionException, TimeoutException, GeneralSecurityException, IOException {
		Assert.assertEquals(SslActionEnum.WAIT_FOR_MORE_DATA_FROM_REMOTE_END, parseIncoming().getSslAction());
		Assert.assertEquals(SslActionEnum.WAIT_FOR_MORE_DATA_FROM_REMOTE_END, parseIncoming().getSslAction());
		
		DataWrapper payload = mockChannel.nextPayload();
		CompletableFuture<List<SslAction>> resultFuture2 = svrSslParser.parseIncoming(payload);
		List<SslAction> result2 = resultFuture2.get(2, TimeUnit.SECONDS);
		Assert.assertEquals(SslActionEnum.SEND_TO_SOCKET, result2.get(0).getSslAction());
		Assert.assertEquals(SslActionEnum.SEND_LINK_ESTABLISHED_TO_APP, result2.get(1).getSslAction());
		
		Assert.assertFalse(connectFuture.isDone()); //client is still NOT connected yet until the SSL handshake final messages are received
		
		mockChannel.forceDataRead(mockJdk, result2.get(0).getEncryptedData());
		
		connectFuture.get(2, TimeUnit.SECONDS);
		
		transferBigData();
	}

	private void transferBigData() throws InterruptedException, ExecutionException, TimeoutException {
		ByteBuffer b = ByteBuffer.allocate(17000);
		b.put((byte) 1);
		b.put((byte) 2);
		b.position(b.limit()-2); //simulate buffer full of 0's except first 2 and last 2
		b.put((byte) 3);
		b.put((byte) 4);
		b.flip();
		
		CompletableFuture<Void> future = channel.write(b);
		future.get(2, TimeUnit.SECONDS);

		//results in two ssl packets going out instead of the one that was fed in..
		SslAction action = parseIncoming();
		SslAction action2 = parseIncoming();
		Assert.assertEquals(SslActionEnum.SEND_TO_APP, action.getSslAction());
		Assert.assertEquals(SslActionEnum.SEND_TO_APP, action2.getSslAction());
		
		Assert.assertEquals(17000, action.getDecryptedData().getReadableSize()+action2.getDecryptedData().getReadableSize());
	}
	
	@Test
	public void testCombineBuffers() throws InterruptedException, ExecutionException, TimeoutException {
		//in this case, combine the output of all 3 of the client engine...
		DataWrapper fullData = dataGen.chainDataWrappers(mockChannel.nextPayload(), mockChannel.nextPayload(), mockChannel.nextPayload());
		
		CompletableFuture<List<SslAction>> resultFuture2 = svrSslParser.parseIncoming(fullData);
		List<SslAction> result2 = resultFuture2.get(2, TimeUnit.SECONDS);
		Assert.assertEquals(SslActionEnum.SEND_TO_SOCKET, result2.get(0).getSslAction());
		Assert.assertEquals(SslActionEnum.SEND_LINK_ESTABLISHED_TO_APP, result2.get(1).getSslAction());
		
		Assert.assertFalse(connectFuture.isDone()); //client is still NOT connected yet until the SSL handshake final messages are received
		
		mockChannel.forceDataRead(mockJdk, result2.get(0).getEncryptedData());
		
		connectFuture.get(2, TimeUnit.SECONDS);
	}
	
	@Test
	public void testHalfThenTooMuchFedInPacket() throws InterruptedException, ExecutionException, TimeoutException {
		List<DataWrapper> packets = reshuffle();

		SslAction action1 = parseIncoming(packets.get(0));
		Assert.assertEquals(SslActionEnum.WAIT_FOR_MORE_DATA_FROM_REMOTE_END, action1.getSslAction());

		SslAction action2 = parseIncoming(packets.get(1));
		Assert.assertEquals(SslActionEnum.WAIT_FOR_MORE_DATA_FROM_REMOTE_END, action2.getSslAction());

		SslAction action3 = parseIncoming(packets.get(2));
		Assert.assertEquals(SslActionEnum.WAIT_FOR_MORE_DATA_FROM_REMOTE_END, action3.getSslAction());

		CompletableFuture<List<SslAction>> resultFuture2 = svrSslParser.parseIncoming(packets.get(3));
		List<SslAction> result2 = resultFuture2.get(2, TimeUnit.SECONDS);
		Assert.assertEquals(SslActionEnum.SEND_TO_SOCKET, result2.get(0).getSslAction());
		Assert.assertEquals(SslActionEnum.SEND_LINK_ESTABLISHED_TO_APP, result2.get(1).getSslAction());
		
		Assert.assertFalse(connectFuture.isDone()); //client is still NOT connected yet until the SSL handshake final messages are received
		
		mockChannel.forceDataRead(mockJdk, result2.get(0).getEncryptedData());
		
		connectFuture.get(2, TimeUnit.SECONDS);
	}
	
	private List<DataWrapper> reshuffle() {
		DataWrapper payload1 = mockChannel.nextPayload(); 
		DataWrapper payload2 = mockChannel.nextPayload(); 
		DataWrapper payload3 = mockChannel.nextPayload(); 
		
		List<DataWrapper> fourDatas = new ArrayList<>();
		List<? extends DataWrapper> split1 = dataGen.split(payload1, payload1.getReadableSize()/2);
		List<? extends DataWrapper> split2 = dataGen.split(payload2, payload2.getReadableSize()/2);
		List<? extends DataWrapper> split3 = dataGen.split(payload3, payload3.getReadableSize()/2);
		
		fourDatas.add(split1.get(0));
		fourDatas.add(dataGen.chainDataWrappers(split1.get(1), split2.get(0)));
		fourDatas.add(dataGen.chainDataWrappers(split2.get(1), split3.get(0)));
		fourDatas.add(split3.get(1));

		return fourDatas;
	}

	private SslAction parseIncoming() throws InterruptedException, ExecutionException, TimeoutException {
		DataWrapper payload = mockChannel.nextPayload();
		CompletableFuture<List<SslAction>> resultFuture2 = svrSslParser.parseIncoming(payload);
		List<SslAction> result2 = resultFuture2.get(2, TimeUnit.SECONDS);
		Assert.assertEquals(1, result2.size());
		return result2.get(0);
	}

	private SslAction parseIncoming(DataWrapper payload) throws InterruptedException, ExecutionException, TimeoutException {
		CompletableFuture<List<SslAction>> resultFuture2 = svrSslParser.parseIncoming(payload);
		List<SslAction> result2 = resultFuture2.get(2, TimeUnit.SECONDS);
		Assert.assertEquals(1, result2.size());
		return result2.get(0);
	}
	
	private ChannelManager createSvrChanMgr(String name) {
		ChannelManagerFactory factory = ChannelManagerFactory.createFactory(mockJdk);
		ChannelManager svrMgr = factory.createMultiThreadedChanMgr(name+"Mgr", new BufferCreationPool(), new BackpressureConfig(), new DirectExecutor());
		return svrMgr;
	}
}
