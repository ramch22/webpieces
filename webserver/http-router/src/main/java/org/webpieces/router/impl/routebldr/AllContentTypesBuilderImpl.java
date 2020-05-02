package org.webpieces.router.impl.routebldr;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.webpieces.router.api.routebldr.ContentTypeRouteBuilder;
import org.webpieces.router.api.routebldr.AllContentTypesBuilder;
import org.webpieces.router.impl.ResettingLogic;
import org.webpieces.router.impl.model.RouteBuilderLogic;
import org.webpieces.router.impl.model.RouterInfo;
import org.webpieces.router.impl.routers.CRouter;
import org.webpieces.router.impl.routers.DRouter;

public class AllContentTypesBuilderImpl implements AllContentTypesBuilder {

	private final RouteBuilderLogic holder;

	private final RouteBuilderImpl leftOverDomainsBuilder;
	private final Map<String, ContentTypeBuilderImpl> domainToRouteBuilder = new HashMap<>();

	private ResettingLogic resettingLogic;

	private String domain;
	
	public AllContentTypesBuilderImpl(String domain, RouteBuilderLogic holder, ResettingLogic resettingLogic) {
		this.domain = domain;
		this.holder = holder;
		this.resettingLogic = resettingLogic;
		this.leftOverDomainsBuilder = new RouteBuilderImpl(domain+":<anycontent>", holder, resettingLogic);
	}

	@Override
	public RouteBuilderImpl getBldrForAllOtherContentTypes() {
		return leftOverDomainsBuilder;
	}

	@Override
	public ContentTypeRouteBuilder getContentTypeRtBldr(String requestContentType) {
		ContentTypeBuilderImpl builder = domainToRouteBuilder.get(requestContentType);
		if(builder != null)
			return builder;
		
		builder = new ContentTypeBuilderImpl(new RouterInfo(domain+":"+requestContentType, ""), holder, resettingLogic);
		domainToRouteBuilder.put(requestContentType, builder);
		return builder;
	}

	public CRouter buildRouter() {
		DRouter router = leftOverDomainsBuilder.buildRouter();
		
		Map<String, DRouter> domainToRouter = new HashMap<>();
		for(Entry<String, ContentTypeBuilderImpl> entry : domainToRouteBuilder.entrySet()) {
			DRouter router2 = entry.getValue().buildRouter();
			domainToRouter.put(entry.getKey(), router2);
		}
		
		return new CRouter(router, domainToRouter);
	}

}
