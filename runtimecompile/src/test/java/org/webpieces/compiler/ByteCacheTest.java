package org.webpieces.compiler;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * NOTE: I am not sure we want it to cache byte code only on class loading rather than compiling but then again if we are
 * not using it do we need it in the cache.  However, if our program exits, we lose it from the cache as well over restarts
 * where the source code has not changed.  Then again, we most likely exist AFTER classloading happens anyways as we
 * load the controller and then execute it (but not all paths will be run).  Anyways, this is how we do it for now
 * 
 * @author dhiller
 *
 */
public class ByteCacheTest extends AbstractCompileTest {

	File byteCodeControllerFile = new File(byteCodeCacheDir, "org.webpieces.compiler.bytecache.ByteCacheController");
	File byteCodeEnumFile = new File(byteCodeCacheDir, "org.webpieces.compiler.bytecache.SomeRouteId");
	
	@Override
	protected String getPackageFilter() {
		return "org.webpieces.compiler.bytecache";
	}
	
	@After
	public void tearDown() {
		byteCodeControllerFile.delete();
		byteCodeEnumFile.delete();
	}
	
	@SuppressWarnings("rawtypes")
	@Test
	public void testByteCodeExistsAtCorrectTime() {
		
		Assert.assertFalse(byteCodeControllerFile.exists());
		Assert.assertFalse(byteCodeEnumFile.exists());
		
		log.info("loading class AddFileController");
		String controller = getPackageFilter()+".ByteCacheController";
		Class c = compiler.loadClass(controller);

		Assert.assertTrue(byteCodeControllerFile.exists());
		Assert.assertFalse(byteCodeEnumFile.exists());		
		
		log.info("loaded");
		invokeMethod(c, "createUserForm");
		
		Assert.assertTrue(byteCodeControllerFile.exists());
		Assert.assertTrue(byteCodeEnumFile.exists());
	}

}
