package org.webpieces.nio.api.mocks;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class MockSelectionKey extends SelectionKey {

	private int readyOps;
	private int interestOps;

	public MockSelectionKey() {
	}

	@Override
	public SelectableChannel channel() {
		throw new UnsupportedOperationException("not supported");
	}

	@Override
	public Selector selector() {
		throw new UnsupportedOperationException("not supported");
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public void cancel() {
	}

	@Override
	public int interestOps() {
		return interestOps;
	}

	@Override
	public SelectionKey interestOps(int ops) {
		interestOps = ops;
		return this;
	}

	@Override
	public int readyOps() {
		return readyOps;
	}

	public void setReadyToConnect() {
		readyOps = readyOps | OP_CONNECT;
	}

	public void setReadyToWrite() {
		readyOps = readyOps | OP_WRITE;
	}

	public void setReadyToRead() {
		readyOps = readyOps | OP_READ;
	}

	public void setReadyToAccept() {
		readyOps = readyOps | OP_ACCEPT;
	}
}
