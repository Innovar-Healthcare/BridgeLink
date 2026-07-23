package com.mirth.connect.smoketest;

/**
 * HL7v2 message corpus for the smoke-test harness (Phase 18, plan 18-06).
 *
 * <p>Both constants below are content copies of the same ORU^R01 message used by the
 * server test suite's {@code TEST_HL7_MESSAGE} constant, with only the segment
 * separator differing:
 * <ul>
 *   <li>{@link #ORU_R01_LF} — {@code \n} separators, matching
 *       {@code server/test/com/mirth/connect/server/controllers/TestUtils.java} line 86.
 *       Used for pumps where the transport does not require MLLP framing (HTTP, File,
 *       REST {@code processMessage}).</li>
 *   <li>{@link #ORU_R01_CR} — {@code \r} separators, matching
 *       {@code donkey/src/test/java/com/mirth/connect/donkey/test/util/TestUtils.java}
 *       line 96. HL7v2-over-MLLP requires {@code \r} segment terminators.</li>
 * </ul>
 *
 * <p>{@link #EXPECTED_PATIENT} is the canonical L2 (destination-content) assertion
 * token — the PID segment's patient last name, "McDoogal" — used across 18-07's
 * per-channel assertions to confirm the transformed payload reached its destination.
 */
public final class Hl7Messages {

    /** ORU^R01, LF-separated segments (HTTP/File/REST processMessage pumps). */
    public static final String ORU_R01_LF = "MSH|^~\\&|LABNET|Acme Labs|||20090601105700||ORU^R01|HMCDOOGAL-0088|D|2.2\nPID|1|8890088|8890088^^^72777||McDoogal^Hattie^||19350118|F||2106-3|100 Beach Drive^Apt. 5^Mission Viejo^CA^92691^US^H||(949) 555-0025|||||8890088^^^72|604422825\nPV1|1|R|C3E^C315^B||||2^HIBBARD^JULIUS^|5^ZIMMERMAN^JOE^|9^ZOIDBERG^JOHN^|CAR||||4|||2301^OBRIEN, KEVIN C|I|1783332658^1^1||||||||||||||||||||DISNEY CLINIC||N|||20090514205600\nORC|RE|928272608|056696716^LA||CM||||20090601105600||||  C3E|||^RESULT PERFORMED\nOBR|1|928272608|056696716^LA|1001520^K|||20090601101300|||MLH25|||HEMOLYZED/VP REDRAW|20090601102400||2301^OBRIEN, KEVIN C||||01123085310001100100152023509915823509915800000000101|0000915200932|20090601105600||LAB|F||^^^20090601084100^^ST~^^^^^ST\nOBX|1|NM|1001520^K||5.3|MMOL/L|3.5-5.5||||F|||20090601105600|IIM|IIM";

    /** ORU^R01, CR-separated segments (HL7v2-over-MLLP pumps). */
    public static final String ORU_R01_CR = "MSH|^~\\&|LABNET|Acme Labs|||20090601105700||ORU^R01|HMCDOOGAL-0088|D|2.2\rPID|1|8890088|8890088^^^72777||McDoogal^Hattie^||19350118|F||2106-3|100 Beach Drive^Apt. 5^Mission Viejo^CA^92691^US^H||(949) 555-0025|||||8890088^^^72|604422825\rPV1|1|R|C3E^C315^B||||2^HIBBARD^JULIUS^|5^ZIMMERMAN^JOE^|9^ZOIDBERG^JOHN^|CAR||||4|||2301^OBRIEN, KEVIN C|I|1783332658^1^1||||||||||||||||||||DISNEY CLINIC||N|||20090514205600\rORC|RE|928272608|056696716^LA||CM||||20090601105600||||  C3E|||^RESULT PERFORMED\rOBR|1|928272608|056696716^LA|1001520^K|||20090601101300|||MLH25|||HEMOLYZED/VP REDRAW|20090601102400||2301^OBRIEN, KEVIN C||||01123085310001100100152023509915823509915800000000101|0000915200932|20090601105600||LAB|F||^^^20090601084100^^ST~^^^^^ST\rOBX|1|NM|1001520^K||5.3|MMOL/L|3.5-5.5||||F|||20090601105600|IIM|IIM\r";

    /** Canonical L2 (destination-content) assertion token: the PID segment's patient last name. */
    public static final String EXPECTED_PATIENT = "McDoogal";

    private Hl7Messages() {
        // constants only
    }
}
