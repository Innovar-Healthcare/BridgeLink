/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.file.filesystems;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Test;

/**
 * Locks the historical contract of the default {@link FileSystemConnection#checkDirectoryAccess}
 * implementation (IRT-1757): connections that do not override it must behave exactly as before,
 * throwing an IOException with no message so the servlet appends no reason. Only overriding
 * connections (SFTP) surface a real reason.
 */
public class FileSystemConnectionTest {

    @Test
    public void testDefaultCheckReadFailureThrowsWithoutReason() throws Exception {
        FileSystemConnection connection = mock(FileSystemConnection.class, CALLS_REAL_METHODS);
        when(connection.canRead("dir")).thenReturn(false);

        try {
            connection.checkDirectoryAccess("dir", true);
            fail("Expected IOException when canRead returns false");
        } catch (IOException e) {
            assertNull("Default implementation must carry no reason", e.getMessage());
        }
    }

    @Test
    public void testDefaultCheckWriteFailureThrowsWithoutReason() throws Exception {
        FileSystemConnection connection = mock(FileSystemConnection.class, CALLS_REAL_METHODS);
        when(connection.canWrite("dir")).thenReturn(false);

        try {
            connection.checkDirectoryAccess("dir", false);
            fail("Expected IOException when canWrite returns false");
        } catch (IOException e) {
            assertNull("Default implementation must carry no reason", e.getMessage());
        }
    }

    @Test
    public void testDefaultCheckReadSucceeds() throws Exception {
        FileSystemConnection connection = mock(FileSystemConnection.class, CALLS_REAL_METHODS);
        when(connection.canRead("dir")).thenReturn(true);

        connection.checkDirectoryAccess("dir", true);
        // no exception == success
    }

    @Test
    public void testDefaultCheckWriteSucceeds() throws Exception {
        FileSystemConnection connection = mock(FileSystemConnection.class, CALLS_REAL_METHODS);
        when(connection.canWrite("dir")).thenReturn(true);

        connection.checkDirectoryAccess("dir", false);
        // no exception == success
    }
}
