package com.lira.vmest;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class InviteCodeTest {
    @Test public void normalizesScannedInviteCodes() {
        assertEquals("A1B2C3D4", InviteCode.normalize("  vmeste:a1b2c3d4 "));
        assertEquals("A1B2C3D4", InviteCode.normalize("a1b2c3d4"));
    }

    @Test public void rejectsInvalidOrIncompleteInviteCodes() {
        assertEquals("", InviteCode.normalize("ABC"));
        assertEquals("", InviteCode.normalize("ABCD-EFGH"));
        assertEquals("", InviteCode.normalize(null));
    }
}
