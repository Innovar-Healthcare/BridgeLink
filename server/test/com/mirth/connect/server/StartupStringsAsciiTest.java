package com.mirth.connect.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/**
 * ASCII-only guard for the server's startup output strings (IRT-2217, criterion 3).
 *
 * Reads Mirth.java and MirthLauncher.java from the working tree and asserts that neither
 * contains the em-dash code point U+2014 (equivalently its UTF-8 byte sequence
 * 0xE2 0x80 0x94). On a non-UTF-8 host, a startup string carrying that byte sequence is what
 * mangles in the console/log output - the team-wide no-em-dash rule exists for exactly this
 * class of string, and this test enforces it mechanically instead of by review alone.
 *
 * The scan is scoped to exactly these two startup source files and never includes this test
 * file itself; the em dash is referenced only via its Java unicode escape (\u2014), never as a
 * literal glyph, so the test file stays ASCII-only and could not flag itself even if it were
 * in scope.
 *
 * Falsifiability, proven manually and reverted byte-for-byte: temporarily reintroducing a
 * single U+2014 character into either Mirth.java or MirthLauncher.java reddens the matching
 * assertion below, proving this is not a tautology.
 */
public class StartupStringsAsciiTest {

    private static final char EM_DASH = '\u2014';

    private static final String MIRTH_JAVA_PATH = "src/com/mirth/connect/server/Mirth.java";
    private static final String MIRTH_LAUNCHER_JAVA_PATH = "src/com/mirth/connect/server/launcher/MirthLauncher.java";

    @Test
    public void mirthJavaContainsNoEmDash() throws Exception {
        assertEquals("Mirth.java must contain zero U+2014 (em dash) characters in its startup strings",
                0, countEmDashes(MIRTH_JAVA_PATH));
    }

    @Test
    public void mirthLauncherJavaContainsNoEmDash() throws Exception {
        assertEquals("MirthLauncher.java must contain zero U+2014 (em dash) characters in its startup strings",
                0, countEmDashes(MIRTH_LAUNCHER_JAVA_PATH));
    }

    private int countEmDashes(String relativePath) throws Exception {
        File sourceFile = new File(relativePath);
        assertTrue("Startup source file must be found at " + sourceFile.getAbsolutePath(), sourceFile.isFile());

        String source = new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);

        int count = 0;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == EM_DASH) {
                count++;
            }
        }
        return count;
    }
}
