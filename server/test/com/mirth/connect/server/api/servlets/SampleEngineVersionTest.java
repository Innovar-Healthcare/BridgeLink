/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 *
 * http://www.mirthcorp.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.server.api.servlets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.junit.Assume;
import org.junit.Test;

/**
 * Every sample extension's metadata must carry the @MIRTH_VERSION@ token that its build.xml fills
 * from server/mirth-build.properties, never a literal version. A literal went stale at the 26.9.0
 * release bump and the engine refused to install the sample zip (IRT-1422).
 *
 * Keyed on the samples directory itself rather than on any one sample, so removing a sample
 * cannot switch the check off for the rest. Skipped when samples/ is not present.
 */
public class SampleEngineVersionTest {

    private static final Pattern MIRTH_VERSION = Pattern.compile("<mirthVersion>([^<]*)</mirthVersion>");

    @Test
    public void testSampleMetadataTakesEngineVersionFromTheBuild() throws Exception {
        // Tests run with the server module as the working directory; the samples tree is a sibling
        File samplesDir = new File("../samples");
        Assume.assumeTrue("samples/ not present — skipping", samplesDir.isDirectory());

        int checked = 0;
        for (File sample : samplesDir.listFiles(File::isDirectory)) {
            for (String metadata : new String[] { "plugin.xml", "source.xml", "destination.xml" }) {
                File file = new File(sample, metadata);
                if (!file.isFile()) {
                    continue;
                }
                Matcher version = MIRTH_VERSION.matcher(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
                assertTrue(sample.getName() + "/" + metadata + " has no mirthVersion", version.find());
                assertEquals(sample.getName() + "/" + metadata + " must not pin an engine version", "@MIRTH_VERSION@", version.group(1));
                checked++;
            }
        }
        assertTrue("found no sample metadata to check", checked > 0);
    }
}
