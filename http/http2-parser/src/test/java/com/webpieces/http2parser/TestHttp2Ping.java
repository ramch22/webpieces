package com.webpieces.http2parser;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.webpieces.data.api.DataWrapper;
import org.webpieces.data.api.TwoPools;

import com.webpieces.http2.api.dto.lowlevel.PingFrame;
import com.webpieces.http2.api.dto.lowlevel.lib.Http2Frame;
import com.webpieces.http2parser.api.Http2Memento;
import com.webpieces.http2parser.api.Http2Parser;
import com.webpieces.http2parser.api.Http2ParserFactory;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class TestHttp2Ping {
	
    private static Http2Parser parser = Http2ParserFactory.createParser(new TwoPools("pl", new SimpleMeterRegistry()));

    private static String getPingFrame() {
            String ping = 
            "00 00 08" + // length
            "06" + // frame type
            "00" + // flags
            "00 00 00 00" + //R + streamid
            "00 00 00 00 00 00 00 10"; // opaqueData
            return ping.replaceAll("\\s+","");
    }

    private static String getPongFrame() {
    	String pong = 
            "00 00 08" + // length
            "06" + // frame type
            "01" + // flags
            "00 00 00 00" + //R + streamid
            "00 00 00 00 00 00 00 10"; // opaqueData
    	
        return pong.replaceAll("\\s+","");
    }

	private Http2Memento memento;

    @Before
    public void setUp() {
    	memento = parser.prepareToParse(Long.MAX_VALUE);    	
    }
    
    @Test
    public void testParsePingFrame() {
    	DataWrapper data = Util.hexToBytes(getPingFrame());
    	parser.parse(memento, data);
    	
    	PingFrame frame = (PingFrame) assertGood();
    	Assert.assertEquals(0, frame.getStreamId());
    	Assert.assertFalse(frame.isPingResponse());
    }
    
    @Test
    public void testParsePongFrame() {
    	DataWrapper data = Util.hexToBytes(getPongFrame());
    	parser.parse(memento, data);
    	
    	PingFrame frame = (PingFrame) assertGood();
    	Assert.assertEquals(0, frame.getStreamId());
    	Assert.assertTrue(frame.isPingResponse());
    }
    
	private Http2Frame assertGood() {
		Assert.assertEquals(0, memento.getLeftOverDataSize());
    	List<Http2Frame> frames = memento.getParsedFrames();
    	Assert.assertEquals(1, frames.size());
    	return frames.get(0);
	}
    
    @Test
    public void testMarshalPingFrame() {
        PingFrame frame = new PingFrame();
        frame.setOpaqueData(0x10);
        
        byte[] data = parser.marshal(frame).createByteArray();
        String hexFrame = Util.toHexString(data);
        Assert.assertEquals(getPingFrame(), hexFrame);
    }

    
    @Test
    public void testMarshalPongFrame() {
        PingFrame frame = new PingFrame();
        frame.setOpaqueData(0x10);
        frame.setIsPingResponse(true);

        byte[] data = parser.marshal(frame).createByteArray();
        String hexFrame = Util.toHexString(data);
        Assert.assertEquals(getPongFrame(), hexFrame);
    }
}
