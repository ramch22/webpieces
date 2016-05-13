package org.webpieces.nio.api;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webpieces.asyncserver.api.AsyncServerManager;
import org.webpieces.asyncserver.api.AsyncServerMgrFactory;
import org.webpieces.netty.api.NettyChannelMgrFactory;
import org.webpieces.nio.api.channels.Channel;
import org.webpieces.nio.api.channels.TCPChannel;
import org.webpieces.nio.api.handlers.DataListener;
import org.webpieces.util.threading.NamedThreadFactory;

import com.webpieces.data.api.BufferCreationPool;
import com.webpieces.data.api.BufferPool;

public class IntegTestLocalhostThroughput {

	private final class ClientDataListener implements DataListener {
		private BufferPool pool2;
		
		public ClientDataListener(BufferPool pool2) {
			this.pool2 = pool2;
		}
		
		@Override
		public void incomingData(Channel channel, ByteBuffer b) {
			recordBytes(b.remaining());
			
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

	private static final Logger log = LoggerFactory.getLogger(IntegTestLocalhostThroughput.class);
	private Timer timer = new Timer();
	private long timeMillis;
	private long totalBytes = 0;
	private long totalBytesLastRount;
	private int counter;
	
	/**
	 * Here, we will simulate a bad hacker client that sets his side so_timeout to infinite
	 * and then refuses to read response data back in but keeps writing into our server to
	 * crash the server as it backs up on responses....ie. we keep receiving requests and holding
	 * on to them so memory keeps growing and growing or our write queue keeps growing unbounded
	 * 
	 * so this test ensures we fix that scenario
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws InterruptedException {
		new IntegTestLocalhostThroughput().testSoTimeoutOnSocket();
	}
	
	public void testSoTimeoutOnSocket() throws InterruptedException {
		Executor executor = Executors.newFixedThreadPool(10, new NamedThreadFactory("serverThread"));
		BufferPool pool = new BufferCreationPool();
		ChannelManagerFactory factory = ChannelManagerFactory.createFactory();
		ChannelManager mgr = factory.createMultiThreadedChanMgr("server", pool, executor);
		AsyncServerManager server = AsyncServerMgrFactory.createAsyncServer(mgr);
		server.createTcpServer("tcpServer", new InetSocketAddress(8080), new IntegTestLocalhostServerListener(pool));
		
		BufferPool pool2 = new BufferCreationPool();
		DataListener listener = new ClientDataListener(pool2);
		Executor executor2 = Executors.newFixedThreadPool(10, new NamedThreadFactory("clientThread"));
		TCPChannel channel = createClientChannel(pool2, executor2);
		//TCPChannel channel = createNettyChannel();

		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				logBytesTxfrd();
			}
		}, 1000, 10000);

		CompletableFuture<Channel> connect = channel.connect(new InetSocketAddress(8080), listener);
		connect.thenAccept(p -> runWriting(channel));
		
		synchronized(this) {
			this.wait();
		}
	}

	private TCPChannel createNettyChannel() {
		org.webpieces.netty.api.BufferPool pool = new org.webpieces.netty.api.BufferPool();
		
		NettyChannelMgrFactory factory = NettyChannelMgrFactory.createFactory();
		ChannelManager mgr = factory.createChannelManager(pool);
		TCPChannel channel = mgr.createTCPChannel("clientChan");
		return channel;		
	}

	private TCPChannel createClientChannel(BufferPool pool2, Executor executor) {
		ChannelManagerFactory factory = ChannelManagerFactory.createFactory();
		ChannelManager mgr = factory.createMultiThreadedChanMgr("client", pool2, executor);
		TCPChannel channel = mgr.createTCPChannel("clientChan");
		return channel;
	}

	private void logBytesTxfrd() {
		long bytesTxfrd = getBytes();
		long bytesThisRound = bytesTxfrd - totalBytesLastRount;
		
		totalBytesLastRount = bytesTxfrd;
		long totalTime = System.currentTimeMillis() - timeMillis;
		long bytesPerMs = bytesTxfrd / totalTime; 
		double megaBytesPerMs = ((double)bytesPerMs) / 1_000_000;
		double megaBytesPerSec = megaBytesPerMs * 1000;
		
		long roundBytesPerMs = bytesThisRound / totalTime;
		double megaRoundPerMs = ((double)roundBytesPerMs) / 1_000_000;
		double megaRoundPerSec = megaRoundPerMs * 1000;
		log.info("time for bytes="+bytesTxfrd+". time="+totalTime+" rate="+megaBytesPerMs+"MBytes/Ms or "+megaBytesPerSec+"MBytes/Sec  this round="+megaRoundPerSec+"MBytes/Sec");
	}
	
	private void runWriting(Channel channel) {
		timeMillis = System.currentTimeMillis();

		log.info("starting writing");
		write(channel, null);
	}

	private synchronized long getBytes() {
		return totalBytes;
	}
	protected synchronized void recordBytes(int remaining) {
		totalBytes += remaining;
	}

	private void write(Channel channel, String reason) {
		counter++;
		if(counter % 5000 == 0)
			log.info("counter="+counter);
		byte[] data = new byte[10240];
		ByteBuffer buffer = ByteBuffer.wrap(data);
		CompletableFuture<Channel> write = channel.write(buffer);
		
		write
			.thenAccept(p -> write(channel, "wrote data from client"))
			.whenComplete((r, e) -> finished(r, e))
			.exceptionally(e -> {
				logIt(e);
				return null;
			});
	}

	private void logIt(Throwable e) {
		log.warn("failed to write", e);
	}

	private void finished(Void r, Throwable e) {
		if(e != null) 
			log.info("failed due to reason="+e.getMessage());
	}
	
}
