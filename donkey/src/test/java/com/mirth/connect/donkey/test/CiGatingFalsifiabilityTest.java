package com.mirth.connect.donkey.test;

import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * THROWAWAY test seeded solely to redden the donkey batchtest fileset for the
 * IRT-1539 docker-cleanup-on-failure falsifiability proof (26.3-02). This class is
 * created and reverted entirely within Plan 26.3-02 and MUST NOT persist past that
 * plan -- it exists only to force `<fail if="test.failed">` in donkey/build.xml so the
 * `<trycatch>`/`<finally>` docker-cleanup-db wrapper in `test-run-db-with-cleanup` can
 * be observed running (or, in the Task 2 revert-proof, observed NOT running) against a
 * real failing build.
 */
public class CiGatingFalsifiabilityTest {

    @Test
    public void seedUnconditionalFailure() {
        fail("IRT-1539 falsifiability seed");
    }
}
