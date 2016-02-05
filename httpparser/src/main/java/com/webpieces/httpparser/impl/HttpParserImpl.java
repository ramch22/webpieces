package com.webpieces.httpparser.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webpieces.httpparser.api.DataWrapper;
import com.webpieces.httpparser.api.DataWrapperGenerator;
import com.webpieces.httpparser.api.HttpParser;
import com.webpieces.httpparser.api.HttpParserFactory;
import com.webpieces.httpparser.api.Memento;
import com.webpieces.httpparser.api.ParseException;
import com.webpieces.httpparser.api.ParsedStatus;
import com.webpieces.httpparser.api.common.Header;
import com.webpieces.httpparser.api.common.KnownHeaderName;
import com.webpieces.httpparser.api.dto.HttpMessage;
import com.webpieces.httpparser.api.dto.HttpMessageType;
import com.webpieces.httpparser.api.dto.HttpRequest;
import com.webpieces.httpparser.api.dto.HttpRequestLine;
import com.webpieces.httpparser.api.dto.HttpRequestMethod;
import com.webpieces.httpparser.api.dto.HttpResponse;
import com.webpieces.httpparser.api.dto.HttpResponseStatus;
import com.webpieces.httpparser.api.dto.HttpResponseStatusLine;
import com.webpieces.httpparser.api.dto.HttpUri;
import com.webpieces.httpparser.api.dto.HttpVersion;
import com.webpieces.httpparser.impl.data.EmptyWrapper;

public class HttpParserImpl implements HttpParser {

	private static final Logger log = LoggerFactory.getLogger(HttpParserImpl.class);
	private static final Charset iso8859_1 = Charset.forName("ISO-8859-1");
	private ConvertAscii conversion = new ConvertAscii();
	private DataWrapperGenerator dataGen = HttpParserFactory.createDataWrapperGenerator();
	
	@Override
	public byte[] marshalToBytes(HttpMessage request) {
		String result = marshalHeaders(request);
		
		DataWrapper body = request.getBody();
		Header header = request.getHeaderLookupStruct().getHeader(KnownHeaderName.CONTENT_LENGTH);
		if(header == null && body != null) {
			throw new IllegalArgumentException("Body provided but no header for KnownHeaderName.CONTENT_LENGTH found");
		} else if(header != null && body == null) {
			throw new IllegalArgumentException("Header KnownHeaderName.CONTENT_LENGTH found but no body was set.  set a body");
		}
		
		int lengthOfBodyFromHeader = 0;
		if(header != null) {
			String value = header.getValue();
			lengthOfBodyFromHeader = toInteger(value, header);
			int actualBodyLength = body.getReadableSize();
			if(lengthOfBodyFromHeader != actualBodyLength) {
				throw new IllegalArgumentException("body size and KnownHeaderName.CONTENT_LENGTH"
						+ " must match.  bodySize="+actualBodyLength+" header len="+lengthOfBodyFromHeader);
			}
		}
		

		//TODO: Is there a way to write a String to first part of byte[] rather than having to copy
		//the byte array from the string into the full byte array
		byte[] stringPiece = result.getBytes(iso8859_1);
		byte[] data = new byte[result.length()+lengthOfBodyFromHeader];
		
		System.arraycopy(stringPiece, 0, data, 0, stringPiece.length);

		int offset = result.length();
		for(int i = 0; i < lengthOfBodyFromHeader; i++) {
			//TODO: Think about using System.arrayCopy here(what is faster?)
			data[offset + i] = body.readByteAt(i);
		}
		
		return data;
	}

	private Integer toInteger(String value, Header header) {
		try {
			return Integer.valueOf(value);
		} catch(NumberFormatException e) {
			throw new IllegalArgumentException("HttpMessage contains illegal header="+header);
		}
	}

	@Override
	public String marshalToString(HttpMessage httpMsg) {
		//TODO: We could check Content-Type header and if text type, we could marshall it still?
		if(httpMsg.getBody() != null)
			throw new IllegalArgumentException("Cannot marshal http message with a body to a string");
		
		return marshalHeaders(httpMsg);
	}

	private String marshalHeaders(HttpMessage httpMsg) {
		if(httpMsg.getMessageType() == HttpMessageType.REQUEST)
			validate(httpMsg.getHttpRequest());
		else if(httpMsg.getMessageType() == HttpMessageType.RESPONSE)
			validate(httpMsg.getHttpResponse());
		
		//TODO: perhaps optimize and use StringBuilder on the Header for loop
		//Java optimizes most to StringBuilder but for a for loop, it doesn't all the time...
		StringBuilder builder = new StringBuilder();
		builder.append(httpMsg + "");
		return builder.toString();
	}

	private void validate(HttpResponse response) {
		HttpResponseStatusLine statusLine = response.getStatusLine();
		if(statusLine == null) {
			throw new IllegalArgumentException("response.statusLine is not set(call response.setStatusLine");
		}
		HttpResponseStatus status = statusLine.getStatus();
		if(status == null) {
			throw new IllegalArgumentException("response.statusLine.status is not set(call response.getStatusLine().setStatus())");
		} else if(status.getCode() == null) {
			throw new IllegalArgumentException("response.statusLine.status.code is not set(call response.getStatusLine().getStatus().setCode())");
		} else if(status.getReason() == null) {
			throw new IllegalArgumentException("response.statusLine.status.reason is not set");
		} else if(statusLine.getVersion() == null) {
			throw new IllegalArgumentException("response.statusLine.version is not set");
		}
	}

	private void validate(HttpRequest request) {
		HttpRequestLine requestLine = request.getRequestLine();
		if(requestLine == null) {
			throw new IllegalArgumentException("request.requestLine is not set(call request.setRequestLine()");
		} else if(requestLine.getMethod() == null) {
			throw new IllegalArgumentException("request.requestLine.method is not set(call request.getRequestLine().setMethod()");
		} else if(requestLine.getVersion() == null) {
			throw new IllegalArgumentException("request.requestLine.version is not set(call request.getRequestLine().setVersion()");
		}
	}

	public Memento prepareToParse() {
		MementoImpl memento = new MementoImpl();
		memento.setLeftOverData(dataGen.emptyWrapper());
		return memento;
	}
	
	@Override
	public Memento parse(Memento state, DataWrapper moreData) {
		if(!(state instanceof MementoImpl)) {
			throw new IllegalArgumentException("You must always pass in the "
					+ "memento created in prepareToParse which we always hand back"
					+ "to you from this method.  It contains state of leftover data");
		}
		
		if(log.isDebugEnabled()) {
			byte[] someData = moreData.createByteArray();
			String readable = conversion.convertToReadableForm(someData);
			log.info("about to parse=\n\n'"+readable+"'\n\n");
		}

		MementoImpl memento = (MementoImpl) state;
		//initialize state to need more data
		memento.setStatus(ParsedStatus.NEED_MORE_DATA);
		memento.getParsedMessages().clear();
		
		DataWrapper leftOverData = memento.getLeftOverData();
		DataWrapper	allData = dataGen.chainDataWrappers(leftOverData, moreData);
		memento.setLeftOverData(allData);
		
		if(memento.getHalfParsedMessage() != null)
			throw new UnsupportedOperationException("not complete yet");
		
		return findCrLnCrLnAndParseMessage(memento);
	}

	private MementoImpl findCrLnCrLnAndParseMessage(MementoImpl memento) {
		//We are looking for the \r\n\r\n  (or \n\n from bad systems) to
		//discover entire payload
		for(int i = 0; i < memento.getLeftOverData().getReadableSize() - 3; i++) {
			processUntilRead(memento, i);
			//do not continue if we are reading the body...
			if(memento.getHalfParsedMessage() != null) {
				break;
			}
		}
		
		DataWrapper leftOverData = memento.getLeftOverData();
		if(leftOverData instanceof EmptyWrapper) {
			memento.setStatus(ParsedStatus.ALL_DATA_PARSED);
		}
		return memento;
	}

	private void processUntilRead(MementoImpl memento, int i) {
		DataWrapper dataToRead = memento.getLeftOverData();
		byte firstByte = dataToRead.readByteAt(i);
		byte secondByte = dataToRead.readByteAt(i+1);
		byte thirdByte = dataToRead.readByteAt(i+2);
		byte fourthByte = dataToRead.readByteAt(i+3);
		
		//For debugging to see the 4 bytes that we are processing easier
//		byte[] data = dataToRead.createByteArray();
//		String fourBytesAre = conversion.convertToReadableForm(data, i, 4);
		
		boolean isFirstCr = conversion.isCarriageReturn(firstByte);
		boolean isSecondLineFeed = conversion.isLineFeed(secondByte);
		boolean isThirdCr = conversion.isCarriageReturn(thirdByte);
		boolean isFourthLineField = conversion.isLineFeed(fourthByte);
		
		if(isFirstCr && isSecondLineFeed && isThirdCr && isFourthLineField) {
			//Found end of http headers...
			processHttpMessageAndMaybeBody(memento, dataToRead, i);
			return;
		}
		
		//mark any positions for \r\n
		if(isFirstCr && isSecondLineFeed) {
			memento.addDemarcation(i);
		}		
	}

	private void processHttpMessageAndMaybeBody(MementoImpl memento, DataWrapper dataToRead, int i) {
		//A FEW ways to go here with http body....  I could 
		//1. pass back an http message while body is being filled in
		//    a. have client call getBody and block until filled or
		//    b. have client call getInputStream and block on inputStream.read
		//    c. have client call addBodyCallback(callback) such that client would unblock and
		//           we call into client with bytes, more bytes, more bytes
		//2. only pass back http message once full body is in place
		// 
		//NOTE: because of http body content and encodings, only the full body is zipped so
		//need full body anyways to unzip.  (ie. chunking is used instead of body for streaming
		//so for now, just read in entire body before returning it to the client
		
		List<Integer> markedPositions = memento.getLeftOverMarkedPositions();
		memento.setLeftOverMarkedPositions(new ArrayList<Integer>());
		List<DataWrapper> tuple = dataGen.split(dataToRead, i+4);
		DataWrapper toBeParsed = tuple.get(0);
		memento.setLeftOverData(tuple.get(1));
		HttpMessage message = parseHttpMessage(toBeParsed, markedPositions);
		Header header = message.getHeaderLookupStruct().getHeader(KnownHeaderName.CONTENT_LENGTH);
		if(header == null) {
			//no body in the bytestream so add the message to list of parsed messages
			memento.getParsedMessages().add(message);
			return;
		}

		boolean readEntireBody = readInBody(message, memento, header);
		if (readEntireBody) {
			memento.getParsedMessages().add(message);
			return;
		}
		
		memento.setHalfParsedMessage(message);
	}

	private boolean readInBody(HttpMessage message, MementoImpl memento, Header header) {
		DataWrapper dataToRead = memento.getLeftOverData();
		String value = header.getValue();
		int length = toInteger(value, header);
		int readableSize = dataToRead.getReadableSize();
		
		if(length <= readableSize) {
			List<DataWrapper> split = dataGen.split(dataToRead, length);
			message.setBody(split.get(0));
			memento.setLeftOverData(split.get(1));
			memento.setNumBytesLeftToRead(0);
			return true;
		}

		//we didn't read anything yet since there is no data
		memento.setNumBytesLeftToRead(length);
		return false;
	}

	private HttpMessage parseHttpMessage(DataWrapper toBeParsed, List<Integer> markedPositions) {
		List<String> lines = new ArrayList<>();
		
		//Add the last line..
		markedPositions.add(toBeParsed.getReadableSize());
		int offset = 0;
		for(Integer mark : markedPositions) {
			int len = mark - offset;
			String line = toBeParsed.createStringFrom(offset, len, iso8859_1);
			lines.add(line.trim());
			offset = mark;
		}
		markedPositions.clear();

		String firstLine = lines.get(0);
		String[] pieces = firstLine.split("\\s+");
		
		if(pieces[0].startsWith("HTTP/")) {
			return parseResponse(lines, pieces);
		} else {
			return parseRequest(lines, pieces);
		}
	}

	private HttpMessage parseRequest(List<String> lines, String[] firstLinePieces) {
		//remove first line...
		String firstLine = lines.remove(0);
		if(firstLinePieces.length != 3) {
			throw new ParseException("Unable to parse invalid http request due to first line being invalid=" + firstLine);
		}
		
		HttpRequestMethod method = HttpRequestMethod.valueOf(firstLinePieces[0]);
		if(method == null) {
			throw new ParseException("Invalid method in request line of http request.  method="+firstLinePieces[0]);
		}
		
		HttpUri uri = new HttpUri(firstLinePieces[1]);
		
		if(!firstLinePieces[2].startsWith("HTTP/")) {
			throw new ParseException("Invalid version in http request first line not prefixed with HTTP/.  line="+firstLine);
		}
		
		String ver = firstLinePieces[2].substring(5, firstLinePieces[2].length());
		HttpVersion version = new HttpVersion();
		version.setVersion(ver);
		
		HttpRequestLine httpRequestLine = new HttpRequestLine();
		httpRequestLine.setMethod(method);
		httpRequestLine.setUri(uri);
		httpRequestLine.setVersion(version);
		
		HttpRequest request = new HttpRequest();
		request.setRequestLine(httpRequestLine);
		
		//TODO: one header can be multiline and we need to fix this code for that
		for(String line : lines) {
			Header header = parseHeader(line);
			request.addHeader(header);
		}
		
		return request;
	}

	private Header parseHeader(String line) {
		//can't use split in case there are two ':' ...one in the value and one as the delimeter
		int indexOf = line.indexOf(":");
		String value = line.substring(indexOf+1).trim();
		String name = line.substring(0, indexOf);
		Header header = new Header();
		header.setName(name.trim());
		header.setValue(value.trim());
		return header;
	}

	private HttpMessage parseResponse(List<String> lines, String[] pieces) {
		throw new UnsupportedOperationException("not supported");
	}

	@Override
	public HttpMessage unmarshal(byte[] msg) {
		Memento memento = prepareToParse();
		DataWrapper dataWrapper = dataGen.wrapByteArray(msg);
		Memento parsedData = parse(memento, dataWrapper);
		if(parsedData.getStatus() == ParsedStatus.MSG_PARSED_AND_LEFTOVER_DATA)
			throw new IllegalArgumentException("There is more data than one http message.  Use unmarshalAsync instead");
		else if(parsedData.getStatus() == ParsedStatus.NEED_MORE_DATA)
			throw new IllegalArgumentException("This http message is not complete.  Use unmarshalAsynch instead or "
					+ "fix client code to pass in complete http message(or report a bug if it is this libraries fault)");
		
		List<HttpMessage> messages = parsedData.getParsedMessages();
		if(messages.size() != 1)
			throw new IllegalArgumentException("You passed in data for more than one http messages.  number of http messages="+messages.size());
		return messages.get(0);
	}
}
