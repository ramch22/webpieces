package com.webpieces.http2parser.api.dto;

import org.webpieces.data.api.DataWrapper;

import com.webpieces.http2parser.api.dto.lib.AbstractHttp2Frame;
import com.webpieces.http2parser.api.dto.lib.Http2FrameType;
import com.webpieces.http2parser.api.dto.lib.Http2MsgType;
import com.webpieces.http2parser.api.dto.lib.PartialStream;

public class DataFrame extends AbstractHttp2Frame implements PartialStream {

    /* flags */
    private boolean endOfStream = false; /* 0x1 */
    //private boolean padded = false;    /* 0x8 */
    /* payload */
    private DataWrapper data = dataGen.emptyWrapper();
    private DataWrapper padding = dataGen.emptyWrapper();

    public boolean isEndOfStream() {
        return endOfStream;
    }

    public void setEndOfStream(boolean endStream) {
        this.endOfStream = endStream;
    }

    public DataWrapper getData() {
        return data;
    }

    public void setData(DataWrapper data) {
        this.data = data;
    }

    public DataWrapper getPadding() {
		return padding;
	}

	public void setPadding(DataWrapper padding) {
		this.padding = padding;
	}

    @Override
    public Http2FrameType getFrameType() {
        return Http2FrameType.DATA;
    }
    @Override
	public Http2MsgType getMessageType() {
		return Http2MsgType.DATA;
	}
	
	@Override
    public String toString() {
        return "DataFrame{" +
        		super.toString() +
                ", endStream=" + endOfStream +
                ", data.len=" + data.getReadableSize() +
                ", padding=" + padding.getReadableSize() +
                "} ";
    }

	public long getTransmitFrameLength() {
		long len = data.getReadableSize();
		long padLen = padding.getReadableSize();
		if(padLen > 0)
			padLen += 1;
		return len + padLen;
	}

}