/*******************************************************************************
 * Copyright (c) 2014, 2016 Sebastian Stenzel, Markus Kreusch
 * This file is licensed under the terms of the MIT license.
 * See the LICENSE.txt file for more info.
 * 
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *     Markus Kreusch - Refactored WebDavMounter to use strategy pattern
 *     Mohit Raju - Added fallback schema-name "webdav" when opening file managers
 ******************************************************************************/
package org.cryptomator.frontend.webdav.mount;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.SystemUtils;
import org.cryptomator.frontend.CommandFailedException;
import org.cryptomator.frontend.Frontend.MountParam;
import org.cryptomator.frontend.webdav.mount.command.Script;

@Singleton
final class LinuxGvfsDavMounter implements WebDavMounterStrategy {

	@Inject
	LinuxGvfsDavMounter() {
	}

	@Override
	public boolean shouldWork(Map<MountParam, Optional<String>> mountParams) {
		if (SystemUtils.IS_OS_LINUX) {
			Optional<String> prefScheme = mountParams.getOrDefault(MountParam.PREFERRED_GVFS_SCHEME, Optional.empty());
			boolean prefSchemeIsUnspecifiedOrDav = !prefScheme.isPresent() || prefScheme.get().equalsIgnoreCase("dav");
			final Script checkScripts = Script.fromLines("which gvfs-mount xdg-open");
			try {
				checkScripts.execute();
				return prefSchemeIsUnspecifiedOrDav;
			} catch (CommandFailedException e) {
				return false;
			}
		} else {
			return false;
		}
	}

	@Override
	public void warmUp(int serverPort) {
		// no-op
	}

	@Override
	public WebDavMount mount(URI uri, Map<MountParam, Optional<String>> mountParams) throws CommandFailedException {
		final Script mountScript = Script.fromLines("set -x", "gvfs-mount \"dav:$DAV_SSP\"").addEnv("DAV_SSP", uri.getRawSchemeSpecificPart());
		mountScript.execute();
		return new LinuxGvfsDavMount(uri);
	}

	private static class LinuxGvfsDavMount extends AbstractWebDavMount {
		private final URI webDavUri;
		private final Script testMountStillExistsScript;
		private final Script unmountScript;

		private LinuxGvfsDavMount(URI webDavUri) {
			this.webDavUri = webDavUri;
			this.testMountStillExistsScript = Script.fromLines("set -x", "test `gvfs-mount --list | grep \"$DAV_SSP\" | wc -l` -eq 1").addEnv("DAV_SSP", webDavUri.getRawSchemeSpecificPart());
			this.unmountScript = Script.fromLines("set -x", "gvfs-mount -u \"dav:$DAV_SSP\"").addEnv("DAV_SSP", webDavUri.getRawSchemeSpecificPart());
		}

		@Override
		public void unmount() throws CommandFailedException {
			boolean mountStillExists;
			try {
				testMountStillExistsScript.execute();
				mountStillExists = true;
			} catch (CommandFailedException e) {
				mountStillExists = false;
			}
			// only attempt unmount if user didn't unmount manually:
			if (mountStillExists) {
				unmountScript.execute();
			}
		}

		@Override
		public void reveal() throws CommandFailedException {
			Script.fromLines("set -x", "gvfs-open \"dav:$DAV_SSP\"").addEnv("DAV_SSP", webDavUri.getRawSchemeSpecificPart()).execute();
		}

	}

}
