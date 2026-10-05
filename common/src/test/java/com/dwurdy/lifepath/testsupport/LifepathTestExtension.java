package com.dwurdy.lifepath.testsupport;

import com.dwurdy.lifepath.client.platform.ClientPlatform;
import com.dwurdy.lifepath.platform.Platform;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Auto-detected JUnit extension (see {@code junit-platform.properties}):
 * installs the noop platform stubs once per JVM so tests can exercise
 * common code that consults the platform seam — mirroring the old state
 * where fabric-loader was on the test classpath.
 */
public class LifepathTestExtension implements BeforeAllCallback {
	@Override
	public void beforeAll(ExtensionContext context) {
		if (!Platform.isInitialized()) {
			Platform.init(new NoopPlatformServices());
		}
		if (!ClientPlatform.isInitialized()) {
			ClientPlatform.init(new NoopClientPlatformServices());
		}
	}
}
