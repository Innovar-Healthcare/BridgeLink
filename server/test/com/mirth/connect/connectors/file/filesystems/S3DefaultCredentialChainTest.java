/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.file.filesystems;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * Owner of the IRT-2573 facts about the vendored AWS SDK v2.
 * <ul>
 * <li>The SDK's default credential chain and its EC2MetadataUtils class must initialize against
 * Core's vendored Jackson. The SDK that shipped in 26.9.0 development read a PropertyNamingStrategy
 * field that Jackson 2.18 removed, so the S3 File connector failed on an EC2 IAM role with a
 * NoSuchFieldError. The current SDK shades its own Jackson and does not link against Core's.</li>
 * <li>The chain is isolated from the machine running the test: temporary empty credential and
 * config files, a closed localhost metadata endpoint, and cleared key properties. In a clean
 * environment the test makes no network call and never reads the user's AWS configuration
 * directory; AWS environment variables cannot be unset in-process and are only tolerated.</li>
 * <li>apache-client must remain the only synchronous HTTP implementation on the classpath, because
 * the SDK ranks any other one above it for every client built without an explicit HTTP client.</li>
 * </ul>
 */
public class S3DefaultCredentialChainTest {

    private static final String EC2_METADATA_UTILS = "software.amazon.awssdk.regions.internal.util.EC2MetadataUtils";
    private static final String APACHE5_SDK_HTTP_SERVICE = "software.amazon.awssdk.http.apache5.Apache5SdkHttpService";
    private static final String[] ISOLATED_PROPERTIES = { "aws.sharedCredentialsFile", "aws.configFile",
            "aws.ec2MetadataServiceEndpoint", "aws.accessKeyId", "aws.secretAccessKey", "aws.sessionToken" };

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final Map<String, String> saved = new HashMap<String, String>();

    @Before
    public void isolate() throws Exception {
        for (String key : ISOLATED_PROPERTIES) {
            saved.put(key, System.getProperty(key));
            System.clearProperty(key);
        }
        File credentials = tempFolder.newFile("credentials");
        File config = tempFolder.newFile("config");
        System.setProperty("aws.sharedCredentialsFile", credentials.getAbsolutePath());
        System.setProperty("aws.configFile", config.getAbsolutePath());
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        System.setProperty("aws.ec2MetadataServiceEndpoint", "http://127.0.0.1:" + closedPort);
    }

    @After
    public void restore() {
        for (Map.Entry<String, String> entry : saved.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    @Test
    public void ec2MetadataUtilsInitializesAgainstVendoredJackson() {
        try {
            Class.forName(EC2_METADATA_UTILS, true, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            // Commercial plugins call this class directly, so a rename must fail here, not at a customer site.
            fail("EC2MetadataUtils is missing from the classpath (IRT-2573): " + e);
        } catch (LinkageError e) {
            fail("EC2MetadataUtils failed to initialize (IRT-2573): " + e);
        }
    }

    @Test
    public void instanceProfileProviderFailsWithSdkClientExceptionWhenImdsUnreachable() {
        InstanceProfileCredentialsProvider provider = InstanceProfileCredentialsProvider.builder().build();
        try {
            provider.resolveCredentials();
            fail("Resolved instance-profile credentials from an unreachable IMDS endpoint");
        } catch (SdkClientException expected) {
            // correct: a clean client-side failure
        } catch (LinkageError e) {
            fail("Instance-profile provider threw a linkage error (IRT-2573): " + e);
        } finally {
            provider.close();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void defaultChainFailsWithSdkClientExceptionNotError() {
        boolean environmentSupplied = System.getenv("AWS_ACCESS_KEY_ID") != null || System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE") != null
                || System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") != null || System.getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI") != null;
        try {
            // Same call S3Connection.createCredentialsProvider makes.
            DefaultCredentialsProvider.create().resolveCredentials();
            assertTrue("Default chain resolved credentials with no source configured", environmentSupplied);
        } catch (SdkClientException expected) {
            // correct: "Unable to load credentials from any of the providers in the chain"
        } catch (LinkageError e) {
            fail("Default credential chain threw a linkage error (IRT-2573): " + e);
        }
    }

    @Test
    public void apache5SyncHttpServiceIsAbsentFromClasspath() {
        try {
            Class.forName(APACHE5_SDK_HTTP_SERVICE, false, getClass().getClassLoader());
            fail("apache5-client is on the classpath and the SDK would rank Apache5SdkHttpService above ApacheSdkHttpService for every client built without an explicit HTTP client (IRT-2573)");
        } catch (ClassNotFoundException expected) {
            // correct: apache-client stays the only synchronous HTTP implementation
        } catch (LinkageError e) {
            fail("apache5-client is on the classpath and the SDK would rank Apache5SdkHttpService above ApacheSdkHttpService for every client built without an explicit HTTP client (IRT-2573): " + e);
        }
    }
}
