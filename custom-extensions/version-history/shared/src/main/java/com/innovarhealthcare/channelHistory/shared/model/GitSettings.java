/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.innovarhealthcare.channelHistory.shared.model;

import org.apache.commons.lang3.StringUtils;

public class GitSettings {

    public static final String AUTH_TYPE_SSH = "SSH";
    public static final String AUTH_TYPE_HTTPS = "HTTPS";

    private String remoteRepositoryUrl;
    private String branchName;
    private String sshPrivateKey;
    private String sshPrivateKeyPath = "";
    private String authType;
    private String httpsUsername;
    private String httpsPassword;
    private String httpsCredentialsPath;

    public GitSettings(String remoteRepositoryUrl, String branchName, String sshPrivateKey) {
        this.remoteRepositoryUrl = remoteRepositoryUrl != null ? remoteRepositoryUrl : "";
        this.branchName = branchName != null ? branchName : "";
        this.sshPrivateKey = sshPrivateKey != null ? sshPrivateKey : "";
        this.authType = "";
        this.httpsUsername = "";
        this.httpsPassword = "";
        this.httpsCredentialsPath = "";
    }

    public String getRemoteRepositoryUrl() {
        return remoteRepositoryUrl;
    }

    public void setRemoteRepositoryUrl(String remoteRepositoryUrl) {
        this.remoteRepositoryUrl = remoteRepositoryUrl;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getSshPrivateKey() {
        return sshPrivateKey;
    }

    public void setSshPrivateKey(String sshPrivateKey) {
        this.sshPrivateKey = sshPrivateKey;
    }

    public String getSshPrivateKeyPath() {
        return sshPrivateKeyPath;
    }

    public void setSshPrivateKeyPath(String sshPrivateKeyPath) {
        this.sshPrivateKeyPath = sshPrivateKeyPath;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getHttpsUsername() {
        return httpsUsername;
    }

    public void setHttpsUsername(String httpsUsername) {
        this.httpsUsername = httpsUsername;
    }

    public String getHttpsPassword() {
        return httpsPassword;
    }

    public void setHttpsPassword(String httpsPassword) {
        this.httpsPassword = httpsPassword;
    }

    public String getHttpsCredentialsPath() {
        return httpsCredentialsPath;
    }

    public void setHttpsCredentialsPath(String httpsCredentialsPath) {
        this.httpsCredentialsPath = httpsCredentialsPath;
    }

    public boolean isSSH() {
        return authType == null || authType.isEmpty() || AUTH_TYPE_SSH.equalsIgnoreCase(authType);
    }

    public boolean isHTTPS() {
        return AUTH_TYPE_HTTPS.equalsIgnoreCase(authType);
    }

    public boolean validate() {
        if (StringUtils.isEmpty(remoteRepositoryUrl) || StringUtils.isEmpty(branchName)) {
            return false;
        }
        if (isSSH()) {
            return !StringUtils.isEmpty(sshPrivateKey) || !StringUtils.isEmpty(sshPrivateKeyPath);
        }
        if (isHTTPS()) {
            boolean directInput = !StringUtils.isEmpty(httpsUsername) && !StringUtils.isEmpty(httpsPassword);
            boolean filePath = !StringUtils.isEmpty(httpsCredentialsPath);
            return directInput || filePath;
        }
        return false;
    }
}
