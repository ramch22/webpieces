package org.webpieces.http2client.api;

import org.webpieces.nio.api.BackpressureConfig;

import com.webpieces.http2engine.api.client.Http2Config;

public class Http2ClientConfig {

	private String id = "http2Client";
	private int numThreads = 20;
	private Http2Config http2Config = new Http2Config();
	private BackpressureConfig backpressureConfig;
	
	public Http2ClientConfig() {
		backpressureConfig = new BackpressureConfig();
		backpressureConfig.setMaxBytes(null); //turn off for clients 
		backpressureConfig.setStartReadingThreshold(null);
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public Http2Config getHttp2Config() {
		return http2Config;
	}
	public void setHttp2Config(Http2Config http2Config) {
		this.http2Config = http2Config;
	}
	public BackpressureConfig getBackpressureConfig() {
		return backpressureConfig;
	}
	public void setBackpressureConfig(BackpressureConfig backpressureConfig) {
		this.backpressureConfig = backpressureConfig;
	}
	public int getNumThreads() {
		return numThreads;
	}
	public void setNumThreads(int numThreads) {
		this.numThreads = numThreads;
	}
	
	
}
