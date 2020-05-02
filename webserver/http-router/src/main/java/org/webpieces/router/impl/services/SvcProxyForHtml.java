package org.webpieces.router.impl.services;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.webpieces.ctx.api.RequestContext;
import org.webpieces.ctx.api.RouterRequest;
import org.webpieces.router.api.RouterConfig;
import org.webpieces.router.api.controller.actions.Action;
import org.webpieces.router.api.exceptions.BadRequestException;
import org.webpieces.router.api.routes.MethodMeta;
import org.webpieces.router.impl.ctx.SessionImpl;
import org.webpieces.router.impl.model.SvcProxyLogic;
import org.webpieces.router.impl.params.ParamToObjectTranslatorImpl;
import org.webpieces.util.filters.Service;
import org.webpieces.util.futures.ExceptionUtil;

public class SvcProxyForHtml implements Service<MethodMeta, Action> {

	private final ParamToObjectTranslatorImpl translator;
	private final RouterConfig config;
	private final ServiceInvoker invoker;
	
	public SvcProxyForHtml(SvcProxyLogic svcProxyLogic) {
		this.translator = svcProxyLogic.getTranslator();
		this.config = svcProxyLogic.getConfig();
		this.invoker = svcProxyLogic.getServiceInvoker();
	}

	@Override
	public CompletableFuture<Action> invoke(MethodMeta meta) {
		return ExceptionUtil.wrap(() -> invokeMethod(meta));
	}

	private CompletableFuture<Action> invokeMethod(MethodMeta meta) 
			throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		RouteInfoForHtml info = (RouteInfoForHtml) meta.getRoute();
		
		tokenCheck(info, meta.getCtx());
		
		Method m = meta.getLoadedController2().getControllerMethod();
		
		//We chose to do this here so any filters ESPECIALLY API filters 
		//can catch and translate api errors and send customers a logical response
		//On top of that ORM plugins can have a transaction filter and then in this
		//createArgs can look up the bean before applying values since it is in
		//the transaction filter
		List<Object> argsResult = translator.createArgs(m, meta.getCtx(), null);
		
		return invoker.invokeAndCoerce(meta.getLoadedController2(), argsResult.toArray());
	}

	/**
	 * This has to be above LoginFilter so LoginFilter can flash the multiPartParams so edits exist through
	 * a login!!
	 * 
	 */
	private void tokenCheck(RouteInfoForHtml info, RequestContext ctx) {
		RouterRequest req = ctx.getRequest();

		if(req.multiPartFields.size() == 0)
			return;

		if(config.isTokenCheckOn() && info.isCheckSecureToken()) {
			String token = ctx.getSession().get(SessionImpl.SECURE_TOKEN_KEY);
			List<String> formToken = req.multiPartFields.get(RequestContext.SECURE_TOKEN_FORM_NAME);
			if(formToken == null)
				throw new BadRequestException("missing form token(or route added without setting checkToken variable to false)"
						+ "...someone posting form without getting it first(hacker or otherwise) OR "
						+ "you are not using the #{form}# tag or the #{secureToken}# tag to secure your forms");
			else if(formToken.size() == 0) {
				throw new BadRequestException("missing form token(or route added without setting checkToken variable to false)"
						+ "...someone posting form without getting it first(hacker or otherwise) OR "
						+ "you are not using the #{form}# tag or the #{secureToken}# tag to secure your forms");				
			} else if(formToken.size() > 1) {
				throw new BadRequestException("Somehow, there are two values for key="+RequestContext.SECURE_TOKEN_FORM_NAME+". This name is reserved.  perhaps your app is using it?");
			}
			
			String formPostedToken = formToken.get(0);
			if(token == null) {
				throw new BadRequestException("Somehow, the cookie is missing the secure token.  key="+SessionImpl.SECURE_TOKEN_KEY+"."
						+ " This token is set in the session when rendering form tags in FormTag.java when calling Current.session().getOrCreateSecureToken();. form token="+formPostedToken);
			} else if(!token.equals(formPostedToken))
				throw new BadRequestException("bad form token...someone posting form with invalid token(hacker or otherwise)");
		}
	}
}
