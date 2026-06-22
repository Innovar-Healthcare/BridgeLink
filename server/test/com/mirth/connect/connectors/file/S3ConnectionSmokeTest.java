/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.file;

import static org.junit.Assert.assertNotNull;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.Test;

/**
 * API-surface smoke test for netty 4.1.133.Final.
 *
 * Exercises the {@link NioEventLoopGroup} and {@link Bootstrap} lifecycle to confirm the upgraded
 * netty JARs used by the S3/AWS connector are API-compatible. No real AWS credentials or channel
 * binding are required.
 */
public class S3ConnectionSmokeTest {

    @Test
    public void testNioEventLoopGroupShutdown() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            assertNotNull(group);
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    @Test
    public void testBootstrapInstantiation() {
        Bootstrap bootstrap = new Bootstrap();
        assertNotNull(bootstrap);
    }
}
