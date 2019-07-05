package org.webpieces.router.impl.routeinvoker;

import java.util.concurrent.CompletableFuture;

import org.webpieces.ctx.api.RequestContext;
import org.webpieces.router.api.ResponseStreamer;
import org.webpieces.router.api.controller.actions.Action;
import org.webpieces.router.api.controller.actions.RenderContent;
import org.webpieces.router.impl.dto.RenderContentResponse;

public class ResponseProcessorContent implements Processor {
	
	private ResponseStreamer responseCb;

	private RequestContext ctx;

	public ResponseProcessorContent(RequestContext ctx, ResponseStreamer responseCb) {
		this.ctx = ctx;
		this.responseCb = responseCb;
	}

	public CompletableFuture<Void> createContentResponse(RenderContent r) {
		RenderContentResponse resp = new RenderContentResponse(r.getContent(), r.getStatusCode(), r.getReason(), r.getMimeType());
		return ContextWrap.wrap(ctx, () -> responseCb.sendRenderContent(resp));
	}

	public CompletableFuture<Void> continueProcessing(Action controllerResponse, ResponseStreamer responseCb) {
		if(!(controllerResponse instanceof RenderContent)) {
			throw new UnsupportedOperationException("Bug, a webpieces developer must have missed writing a "
					+ "precondition check on content routes to assert the correct return types");
		}
		
		return createContentResponse((RenderContent)controllerResponse);
	}
}
