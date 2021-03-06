package org.webpieces.httpparser.api;

import org.junit.Assert;
import org.junit.Test;
import org.webpieces.httpparser.api.dto.HttpUri;
import org.webpieces.httpparser.api.dto.UrlInfo;


public class TestUriParsing {

	@Test
	public void testBasicUrl() {
		HttpUri uri = new HttpUri("http://www.google.com:8080/there/is/cool?at=this&some=that");
		UrlInfo urlInfo = uri.getUriBreakdown();
		Assert.assertEquals("http", urlInfo.getPrefix());
		Assert.assertEquals("www.google.com", urlInfo.getHost());
		Integer val = 8080;
		Assert.assertEquals(val, urlInfo.getPort());
		Assert.assertEquals("/there/is/cool?at=this&some=that", urlInfo.getFullPath());
	}
	@Test
	public void testSlash() {
		HttpUri uri = new HttpUri("/");
		
		UrlInfo urlInfo = uri.getUriBreakdown();
		Assert.assertEquals(null, urlInfo.getPrefix());
		Assert.assertEquals(null, urlInfo.getHost());
		Assert.assertEquals(null, urlInfo.getPort());
		Assert.assertEquals("/", urlInfo.getFullPath());
	}
}
