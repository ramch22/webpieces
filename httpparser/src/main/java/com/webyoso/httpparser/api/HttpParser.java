package com.webyoso.httpparser.api;

public interface HttpParser {

	public byte[] marshalToBytes(HttpRequest request);
	
	public String marshalToString(HttpRequest request);

}
