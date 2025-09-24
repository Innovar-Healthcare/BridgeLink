package com.mirth.connect.plugins.dynamiclookup.server.util;

import java.util.Map;

import com.mirth.connect.plugins.dynamiclookup.shared.model.LookupGroup;

public final class LookupGroupConverter {

	private LookupGroupConverter() {
	}

	public static LookupGroup toLookupGroup(Map<String, String> map) {
		LookupGroup group = new LookupGroup();
		group.setName(trimOrNull(map.get("name")));
		group.setDescription(trimOrNull(map.get("description")));
		group.setVersion(trimOrNull(map.get("version")));

		int cacheSize = 1000; // default
		String cacheSizeStr = trimOrNull(map.get("cacheSize"));
		if (cacheSizeStr != null) {
			try {
				cacheSize = Integer.parseInt(cacheSizeStr);
			} catch (NumberFormatException ignore) {
			}
		}
		group.setCacheSize(cacheSize);

		group.setCachePolicy(trimOrNull(map.get("cachePolicy")));
		return group;
	}

	private static String trimOrNull(String s) {
		if (s == null) {
			return null;
		}

		String t = s.trim();
		return t.isEmpty() ? null : t;
	}
}
