package com.lira.vmest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AvatarImageTransformTest {
    @Test
    public void mapsExifRotationAndMirrorOrientations() {
        assertTransform(1, 0, false);
        assertTransform(2, 0, true);
        assertTransform(3, 180, false);
        assertTransform(4, 180, true);
        assertTransform(5, 90, true);
        assertTransform(6, 90, false);
        assertTransform(7, 270, true);
        assertTransform(8, 270, false);
    }

    @Test
    public void unknownOrientationUsesUnmodifiedImage() {
        AvatarImageTransform transform = AvatarImageTransform.fromExifOrientation(0);
        assertEquals(0, transform.rotationDegrees);
        assertFalse(transform.flipHorizontal);
    }

    private void assertTransform(int orientation, int degrees, boolean mirrored) {
        AvatarImageTransform transform = AvatarImageTransform.fromExifOrientation(orientation);
        assertEquals(degrees, transform.rotationDegrees);
        if (mirrored) assertTrue(transform.flipHorizontal);
        else assertFalse(transform.flipHorizontal);
    }
}
