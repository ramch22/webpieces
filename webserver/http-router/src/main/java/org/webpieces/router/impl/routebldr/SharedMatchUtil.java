package org.webpieces.router.impl.routebldr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webpieces.router.api.controller.actions.Action;
import org.webpieces.router.api.routes.MethodMeta;
import org.webpieces.router.api.routes.Port;
import org.webpieces.router.api.streams.StreamService;
import org.webpieces.router.impl.ResettingLogic;
import org.webpieces.router.impl.dto.RouteType;
import org.webpieces.router.impl.loader.ResolvedMethod;
import org.webpieces.router.impl.model.RouteBuilderLogic;
import org.webpieces.router.impl.routeinvoker.Processor;
import org.webpieces.router.impl.routers.AbstractDynamicRouter;
import org.webpieces.router.impl.routers.AbstractRouter;
import org.webpieces.router.impl.routers.Endpoint;
import org.webpieces.router.impl.routers.MatchInfo;
import org.webpieces.util.filters.Service;

/** 
 * VERY BAAD pattern using inheritance to re-use.  We need to flip and compose this into the things that need it instead
 * once we convert these builders over to GUICE as well.
 * 
 * ADDED BENEFIT...once Builders are in Guice, anyone can quickly fix bugs by overriding Guice classes.  I ran into this once and
 * wanted to test a change without rebuilding ALL webpieces but couldn't as the builders are newed up :(.
 * @author dean
 *
 */
public class SharedMatchUtil {

	private static final Logger log = LoggerFactory.getLogger(SharedMatchUtil.class);

	protected final RouteBuilderLogic holder;
	protected final ResettingLogic resettingLogic;
	protected final List<RouterAndInfo> newDynamicRoutes = new ArrayList<>();

	private FilterComparator comparator = new FilterComparator();
	
	public SharedMatchUtil(RouteBuilderLogic holder, ResettingLogic resettingLogic2) {
		this.holder = holder;
		this.resettingLogic = resettingLogic2;
	}

	protected List<AbstractRouter> buildRoutes(List<FilterInfo<?>> routeFilters) {
		List<AbstractRouter> routers = new ArrayList<>();
		for(RouterAndInfo routerAndInfo : newDynamicRoutes) {
			AbstractDynamicRouter router = routerAndInfo.getRouter();
			MatchInfo matchInfo = router.getMatchInfo();
			String path = matchInfo.getFullPath();
			Port port = matchInfo.getExposedPorts();
			ResolvedMethod methodMeta = routerAndInfo.getMetaAndController().getMethodMeta();
			
			StreamService streamSvc;
			if(routerAndInfo.getRouteType() == RouteType.STREAMING) {
				streamSvc = new StreamProxy(holder.getFutureUtil(), holder.getServiceInvoker());
			} else {
				//Wire in request/response filters at this point
				List<FilterInfo<?>> filters = findMatchingFilters(methodMeta, path, port, routeFilters);
				FilterCreationMeta chainInfo = new FilterCreationMeta(resettingLogic.getInjector(), filters, routerAndInfo.getSvcProxy());
				Service<MethodMeta, Action> service = holder.getFinder().loadFilters(chainInfo);
	
				Processor processor;
				if(routerAndInfo.getRouteType() == RouteType.CONTENT) {
					processor = holder.getResponseProcessorContent();
				} else if(routerAndInfo.getRouteType() == RouteType.HTML) {
					processor = holder.getResponseProcessorHtml();
				} else {
					throw new IllegalStateException("RouteType not supported here="+routerAndInfo.getRouteType());
				}
				
				String i18nBundleName = routerAndInfo.getRouteInfo().getRouteModuleInfo().getI18nBundleName();
				
				Endpoint svc = new Endpoint(service);
				streamSvc = new RequestResponseStream(svc, i18nBundleName, processor, holder.getBodyParsers(), holder.getServiceInvoker());
			}
			
			//Add streaming filters at this point here...
			router.setDynamicInfo(streamSvc);

			routers.add(router);
		}
		
		return routers;
	}

	public List<FilterInfo<?>> findMatchingFilters(ResolvedMethod methodMeta, String path, Port exposedPorts, List<FilterInfo<?>> routeFilters) {
		boolean isHttpsOnly = exposedPorts == Port.HTTPS;
		List<FilterInfo<?>> matchingFilters = new ArrayList<>();
		for(FilterInfo<?> info : routeFilters) {
			if(!info.securityMatch(isHttpsOnly))
				continue; //skip this filter
			
			Pattern patternToMatch = info.getPatternToMatch();
			
			Matcher matcher;
			if(info.isApplyToPackage()) {
				String controllerAndMethod = methodMeta.getControllerStr()+"."+methodMeta.getMethodStr();
				matcher = patternToMatch.matcher(controllerAndMethod);
			} else {
				matcher = patternToMatch.matcher(path);
			}
			
			if(matcher.matches()) {
				if(log.isDebugEnabled())
					log.debug("Adding filter="+info.getFilter()+" to path="+path);
				matchingFilters.add(0, info);
			}
		}
		
		//sort filters according to apply level
		matchingFilters.sort(comparator);
		
		return matchingFilters;
	}
}
