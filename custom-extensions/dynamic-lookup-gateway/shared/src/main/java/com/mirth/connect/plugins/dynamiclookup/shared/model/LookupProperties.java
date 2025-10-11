/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins.dynamiclookup.shared.model;

import java.util.Properties;

public class LookupProperties {

	// ----- Property Keys -----
	public static final String AUDIT_PRUNE_ENABLED = "dynamiclookup.audit.prune.enabled";
	public static final String AUDIT_PRUNE_RETENTION_DAYS = "dynamiclookup.audit.prune.retentionDays";

	// ----- Fields -----
	private boolean auditPruneEnabled;
	private int auditPruneRetentionDays;

	// ----- Constructors -----
	public LookupProperties(boolean auditPruneEnabled, int auditPruneRetentionDays) {
		this.auditPruneEnabled = auditPruneEnabled;
		this.auditPruneRetentionDays = auditPruneRetentionDays;
	}

	// ----- Getters/Setters -----
	public boolean isAuditPruneEnabled() {
		return auditPruneEnabled;
	}

	public void setAuditPruneEnabled(boolean auditPruneEnabled) {
		this.auditPruneEnabled = auditPruneEnabled;
	}

	public int getAuditPruneRetentionDays() {
		return auditPruneRetentionDays;
	}

	public void setAuditPruneRetentionDays(int auditPruneRetentionDays) {
		this.auditPruneRetentionDays = auditPruneRetentionDays;
	}

	// ----- Converters -----
	public Properties toProperties() {
		Properties p = new Properties();
		p.setProperty(AUDIT_PRUNE_ENABLED, Boolean.toString(auditPruneEnabled));
		p.setProperty(AUDIT_PRUNE_RETENTION_DAYS, Integer.toString(auditPruneRetentionDays));
		return p;
	}

	public static LookupProperties fromProperties(Properties p) {
		if (p == null) {
			return getDefault();
		}

		boolean enabled = Boolean.parseBoolean(p.getProperty(AUDIT_PRUNE_ENABLED, "false"));
		int retentionDays;
		try {
			retentionDays = Integer.parseInt(p.getProperty(AUDIT_PRUNE_RETENTION_DAYS, "30"));
		} catch (NumberFormatException e) {
			retentionDays = 30;
		}

		return new LookupProperties(enabled, retentionDays);
	}

	// ----- Defaults -----
	public static LookupProperties getDefault() {
		return new LookupProperties(false, 30);
	}

	// ----- Utility -----
	private LookupProperties() {
		// Prevent instantiation without values
	}
}
