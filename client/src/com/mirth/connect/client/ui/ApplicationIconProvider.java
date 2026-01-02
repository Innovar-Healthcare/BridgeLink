/*
*
* Copyright (c) Innovar Healthcare. All rights reserved.
*
* https://www.innovarhealthcare.com
*
* The software in this package is published under the terms of the MPL license a copy of which has
* been included with this distribution in the LICENSE.txt file.
*/

package com.mirth.connect.client.ui;

import java.awt.Image;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ApplicationIconProvider {

	private static final Logger logger = LogManager.getLogger(ApplicationIconProvider.class);

	private static final String ICON_PROP = "bridgelink.icon.path";

	private static volatile String customIconPath;

	// cache
	private static volatile String cachedPath;
	private static volatile ImageIcon cachedIcon;

	private ApplicationIconProvider() {
	}

	public static void setCustomIconPath(String path) {
		customIconPath = trimToNull(path);
		// force reload next time
		cachedPath = null;
		cachedIcon = null;
		logger.info("Custom icon path set: {}", customIconPath);
	}

	public static ImageIcon getApplicationIcon() {
		String resolved = resolveIconPath();

		// no override -> default
		if (resolved == null) {
			return UIConstants.MIRTH_FAVICON;
		}

		// cache hit
		ImageIcon icon = cachedIcon;
		if (icon != null && resolved.equals(cachedPath)) {
			return icon;
		}

		// load + cache
		ImageIcon loaded = tryLoadIcon(resolved);
		if (loaded != null) {
			cachedPath = resolved;
			cachedIcon = loaded;
			return loaded;
		}

		return UIConstants.MIRTH_FAVICON;
	}

	private static String resolveIconPath() {
		String prop = trimToNull(System.getProperty(ICON_PROP));
		if (prop != null) {
			return prop;
		}

		return trimToNull(customIconPath);
	}

	private static ImageIcon tryLoadIcon(String path) {
		File f = new File(path);

		if (!f.exists()) {
			logger.warn("Icon file does not exist: {}", path);
			return null;
		}

		if (!f.canRead()) {
			logger.warn("Icon file is not readable: {}", path);
			return null;
		}

		try {
			Image img = ImageIO.read(f);
			if (img == null) {
				logger.warn("ImageIO could not decode icon file: {}", path);
				return null;
			}
			return new ImageIcon(img);
		} catch (IOException e) {
			logger.warn("Failed to load icon from path: {}", path, e);
			return null;
		}
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}

		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
