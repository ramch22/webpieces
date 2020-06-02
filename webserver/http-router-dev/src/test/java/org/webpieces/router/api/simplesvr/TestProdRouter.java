package org.webpieces.router.api.simplesvr;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webpieces.ctx.api.HttpMethod;
import org.webpieces.router.api.RouterConfig;
import org.webpieces.router.api.RouterService;
import org.webpieces.router.api.RouterSvcFactory;
import org.webpieces.router.api.TemplateApi;
import org.webpieces.router.api.error.MockStreamHandle;
import org.webpieces.router.api.error.RequestCreation;
import org.webpieces.router.api.extensions.SimpleStorage;
import org.webpieces.router.api.mocks.VirtualFileInputStream;
import org.webpieces.util.cmdline2.Arguments;
import org.webpieces.util.cmdline2.CommandLineParser;
import org.webpieces.util.file.FileFactory;
import org.webpieces.util.file.VirtualFile;
import org.webpieces.util.security.SecretKeyInfo;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.webpieces.hpack.api.dto.Http2Request;
import com.webpieces.hpack.api.dto.Http2Response;
import com.webpieces.http2engine.api.StreamRef;
import com.webpieces.http2engine.api.StreamWriter;
import com.webpieces.http2parser.api.dto.lib.Http2HeaderName;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@RunWith(Parameterized.class)
public class TestProdRouter {
	
	private static final Logger log = LoggerFactory.getLogger(TestProdRouter.class);
	private RouterService server;
	private TestModule overrides;
	
	@SuppressWarnings("rawtypes")
	@Parameterized.Parameters
	public static Collection bothServers() {
		String moduleFileContents = AppModules.class.getName();
		VirtualFile f = new VirtualFileInputStream(moduleFileContents.getBytes(), "testAppModules");		
		
		File baseWorkingDir = FileFactory.getBaseWorkingDir();
		TestModule module = new TestModule();
		Arguments args = new CommandLineParser().parse();
		RouterConfig config = new RouterConfig(baseWorkingDir, "TestProdRouter")
										.setMetaFile(f)
										.setWebappOverrides(module)
										.setSecretKey(SecretKeyInfo.generateForTest());

		SimpleMeterRegistry metrics = new SimpleMeterRegistry();
		TemplateApi nullApi = new NullTemplateApi();
		RouterService prodSvc = RouterSvcFactory.create(metrics, config, nullApi);
		prodSvc.configure(args);
		args.checkConsumedCorrectly();
		
		return Arrays.asList(new Object[][] {
	         { prodSvc, module }
	      });
	}
	
	private static class TestModule implements Module {
		public MockSomeService mockService = new MockSomeService();
		@Override
		public void configure(Binder binder) {
			binder.bind(SomeService.class).toInstance(mockService);
			binder.bind(SimpleStorage.class).toInstance(new EmptyStorage());
		}
	}
	
	public TestProdRouter(RouterService svc, TestModule module) {
		this.server = svc;
		this.overrides = module;
		
		log.info("constructing test class for server="+svc.getClass().getSimpleName());
	}
	
	@Before
	public void setUp() {
		server.start();
	}
	
	/**
	 * This test won't work in DevRoute right now as we need to do the following
	 * 1. create CompileOnDemand very early on 
	 * 2. do a Thread.current().setContextClassLoader(compileOnDemand.getLatestClassloader())
	 * 
	 * and this all needs to be done BEFORE TestModule is created and more importantly before
	 * the bind(SomeService.class) as SomeService will be loaded from one classloader and then
	 * when DEVrouter creates the controller, the compileOnDemand classloader is used resulting
	 * in a mismatch.
	 */
	@Test
	public void testAsyncRouteAndMocking() {
		
		Http2Request req = RequestCreation.createHttpRequest(HttpMethod.GET, "/async");

		//setup returning a response
		CompletableFuture<Integer> future1 = new CompletableFuture<Integer>();
		overrides.mockService.addToReturn(future1);
		
		MockStreamHandle mockStream = new MockStreamHandle();
		StreamRef ref = server.incomingRequest(req, mockStream);
		CompletableFuture<StreamWriter> future = ref.getWriter(); 
		Assert.assertFalse(future.isDone());

		//no response yet...
		Assert.assertNull(mockStream.getLastResponse());

		//release controlleer
		int id = 78888;
		future1.complete(id);

		Assert.assertTrue(future.isDone() && !future.isCompletedExceptionally());
		
		Http2Response resp = mockStream.getLastResponse();
		Assert.assertNull(resp.getSingleHeaderValue(Http2HeaderName.AUTHORITY));
		Assert.assertEquals("http://"+req.getAuthority()+"/meeting/"+id, resp.getSingleHeaderValue(Http2HeaderName.LOCATION));

		//We did not send a keep alive so it should close
		Assert.assertTrue(mockStream.isWasClosed());
	}

}
