/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.file.filesystems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.junit.Test;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;

public class SftpConnectionTest {

    /**
     * IRT-1757: when the directory cannot be opened, checkDirectoryAccess must surface the
     * underlying SFTP reason rather than collapsing it into a bare failure.
     */
    @Test
    public void testCheckDirectoryAccessThrowsWithReasonOnCdFailure() throws Exception {
        ChannelSftp client = mock(ChannelSftp.class);
        doThrow(new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "No such file")).when(client).cd(anyString());

        SftpConnection connection = new SftpConnection(client);

        try {
            connection.checkDirectoryAccess("Uploads", true);
            fail("Expected IOException carrying the SFTP reason");
        } catch (IOException e) {
            assertNotNull("Reason must be preserved for the Test Connection dialog", e.getMessage());
            assertTrue(e.getMessage().contains("No such file"));
        }
    }

    @Test
    public void testCheckDirectoryAccessSucceedsWhenCdSucceeds() throws Exception {
        ChannelSftp client = mock(ChannelSftp.class);
        // client.cd(...) is a no-op void mock -> directory opens cleanly

        SftpConnection connection = new SftpConnection(client);

        connection.checkDirectoryAccess("/home/user/Uploads", false);
        // no exception == success
    }

    /** Write access travels the same cd path, so it must surface the reason too. */
    @Test
    public void testCheckDirectoryAccessWriteThrowsWithReason() throws Exception {
        ChannelSftp client = mock(ChannelSftp.class);
        doThrow(new SftpException(ChannelSftp.SSH_FX_PERMISSION_DENIED, "Permission denied")).when(client).cd(anyString());

        SftpConnection connection = new SftpConnection(client);

        try {
            connection.checkDirectoryAccess("Uploads", false);
            fail("Expected IOException carrying the SFTP reason");
        } catch (IOException e) {
            assertEquals("Permission denied", e.getMessage());
        }
    }
}
