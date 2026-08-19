package com.mirth.connect.donkey.test;

import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * THROWAWAY test seeded solely to redden the donkey batchtest fileset for the
 * IRT-1539 workflow-hardening falsifiability proof (26.3-03, SC#2). This class is
 * created and reverted entirely within Plan 26.3-03 and MUST NOT persist past that
 * plan -- it exists only to force `<fail if="test.failed">` in donkey/build.xml so
 * both the local `ant -f mirth-build.xml` build AND the private-org
 * `build_bridgelink.yml` workflow run turn RED against a real failing build, while
 * the per-module junit-reports (written before `<fail>` fires, since
 * `haltonfailure="false"`) still survive for the `if: always()` upload step.
 */
public class CiGatingFalsifiabilityTest {

    @Test
    public void seedUnconditionalFailure() {
        fail("IRT-1539 falsifiability seed");
    }
}
