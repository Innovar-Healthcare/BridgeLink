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
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mirth.connect.connectors.file.FileSystemConnectionOptions;
import com.mirth.connect.connectors.file.S3SchemeProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Owner of the IRT-2573 fact about closing S3 connections that share the default credential chain.
 * <ul>
 * <li>{@code DefaultCredentialsProvider.create()} is a JVM-wide singleton, and closing an S3Client
 * closes the credentials provider it was built with. On SDK 2.55.8, a connection's
 * {@code destroy()} (every Test Read, undeploy and redeploy) therefore shut the STS client that a
 * role-assuming profile or EKS web identity uses, and every later default-chain connection failed
 * with "Connection pool shut down" once its cached credentials expired, until restart. SDK 2.15.28
 * did not do this. The EC2 instance-profile path is not affected: it reads the metadata service
 * without a pooled client.</li>
 * <li>{@code S3Connection.createCredentialsProvider} hands out a view of the singleton that cannot be
 * closed. This test drives the role-assuming profile path against a local mock STS.</li>
 * <li>It needs a JVM in which the default chain has not been resolved yet, because the chain reads
 * the profile files on first use. The ant build's per-class fork provides that; in an IDE, run it on
 * its own.</li>
 * </ul>
 */
public class S3SharedCredentialsCloseTest {

    private static final String[] ISOLATED_PROPERTIES = { "aws.sharedCredentialsFile", "aws.configFile",
            "aws.ec2MetadataServiceEndpoint", "aws.accessKeyId", "aws.secretAccessKey", "aws.sessionToken", "aws.profile" };
    private static final String[] ENVIRONMENT_OVERRIDES = { "AWS_ACCESS_KEY_ID", "AWS_PROFILE", "AWS_WEB_IDENTITY_TOKEN_FILE",
            "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI", "AWS_CONTAINER_CREDENTIALS_FULL_URI", "AWS_ENDPOINT_URL",
            "AWS_ENDPOINT_URL_STS", "AWS_IGNORE_CONFIGURED_ENDPOINT_URLS" };

    @ClassRule
    public static TemporaryFolder tempFolder = new TemporaryFolder();

    private static final Map<String, String> saved = new HashMap<String, String>();
    private static final AtomicInteger stsCalls = new AtomicInteger();
    private static HttpServer sts;

    @BeforeClass
    public static void startMockStsAndIsolate() throws Exception {
        for (String name : ENVIRONMENT_OVERRIDES) {
            assumeTrue(name + " is set and would override the test profile", System.getenv(name) == null);
        }

        sts = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sts.createContext("/", S3SharedCredentialsCloseTest::assumeRole);
        sts.start();

        File credentials = tempFolder.newFile("credentials");
        Files.write(credentials.toPath(), ("[base]\n" + "aws_access_key_id = AKIAIOSFODNN7EXAMPLE\n"
                + "aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\n").getBytes(StandardCharsets.UTF_8));
        File config = tempFolder.newFile("config");
        Files.write(config.toPath(), ("[default]\n" + "role_arn = arn:aws:iam::123456789012:role/bl-test\n" + "source_profile = base\n"
                + "region = us-east-1\n" + "services = local\n\n" + "[profile base]\n" + "region = us-east-1\n\n" + "[services local]\n"
                + "sts =\n" + "  endpoint_url = http://127.0.0.1:" + sts.getAddress().getPort() + "\n").getBytes(StandardCharsets.UTF_8));

        for (String key : ISOLATED_PROPERTIES) {
            saved.put(key, System.getProperty(key));
            System.clearProperty(key);
        }
        System.setProperty("aws.sharedCredentialsFile", credentials.getAbsolutePath());
        System.setProperty("aws.configFile", config.getAbsolutePath());
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        System.setProperty("aws.ec2MetadataServiceEndpoint", "http://127.0.0.1:" + closedPort);
    }

    @AfterClass
    public static void stopMockStsAndRestore() {
        if (sts != null) {
            sts.stop(0);
        }
        for (Map.Entry<String, String> entry : saved.entrySet()) {
            if (entry.getValue() == null) {
                System.clearProperty(entry.getKey());
            } else {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    /** Answers AssumeRole with credentials that expire in two seconds, so the next resolve must refresh. */
    private static void assumeRole(HttpExchange exchange) throws IOException {
        int call = stsCalls.incrementAndGet();
        exchange.getRequestBody().readAllBytes();
        String body = "<AssumeRoleResponse xmlns=\"https://sts.amazonaws.com/doc/2011-06-15/\"><AssumeRoleResult><Credentials>"
                + "<AccessKeyId>AKIAMOCKSTS" + call + "</AccessKeyId><SecretAccessKey>secret</SecretAccessKey><SessionToken>token</SessionToken>"
                + "<Expiration>" + Instant.now().plusSeconds(2) + "</Expiration></Credentials><AssumedRoleUser>"
                + "<Arn>arn:aws:sts::123456789012:assumed-role/bl-test/session</Arn><AssumedRoleId>AROAMOCK:session</AssumedRoleId>"
                + "</AssumedRoleUser></AssumeRoleResult><ResponseMetadata><RequestId>mock-" + call + "</RequestId></ResponseMetadata>"
                + "</AssumeRoleResponse>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/xml");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static FileSystemConnectionOptions defaultChainOptions() {
        S3SchemeProperties schemeProperties = new S3SchemeProperties();
        schemeProperties.setRegion("us-east-1");
        schemeProperties.setUseDefaultCredentialProviderChain(true);
        return new FileSystemConnectionOptions(false, "", "", schemeProperties);
    }

    private static String resolveAccessKeyId(S3Connection connection) {
        return connection.createCredentialsProvider(defaultChainOptions()).resolveCredentials().accessKeyId();
    }

    @Test
    public void destroyingOneConnectionLeavesSharedChainRefreshingForTheNext() throws Exception {
        // A Test Read, or a channel that is undeployed.
        S3Connection first = new S3Connection(defaultChainOptions(), 5000);
        assertTrue(resolveAccessKeyId(first).startsWith("AKIAMOCKSTS"));
        first.destroy();

        // Unfixed, the closed chain keeps serving its cached credentials until they expire, so wait
        // past expiry to force the refresh that goes through the shut STS client.
        Thread.sleep(3000);
        int callsBefore = stsCalls.get();

        S3Connection next = new S3Connection(defaultChainOptions(), 5000);
        try {
            assertTrue(resolveAccessKeyId(next).startsWith("AKIAMOCKSTS"));
        } catch (RuntimeException e) {
            // Unfixed, this is IllegalStateException: Connection pool shut down.
            fail("Destroying one S3Connection closed the shared default credential chain (IRT-2573): " + e);
        } finally {
            next.destroy();
        }
        assertTrue("Expected a fresh STS call after the cached credentials expired", stsCalls.get() > callsBefore);
    }
}
