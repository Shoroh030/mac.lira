package com.lira.vmest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ReleaseVersionTest {
    @Test public void comparesNumericPartsAndTreatsMissingPatchAsZero() {
        assertTrue(ReleaseVersion.compare("1.2", "1.1.99") > 0);
        assertEquals(0, ReleaseVersion.compare("1.2", "1.2.0"));
        assertTrue(ReleaseVersion.compare("2.0.0", "1.99.99") > 0);
    }

    @Test public void rejectsMalformedOrOverflowingVersions() {
        assertFalse(ReleaseVersion.isValid("latest"));
        assertFalse(ReleaseVersion.isValid("1.2.999999999999999999999"));
    }
}
