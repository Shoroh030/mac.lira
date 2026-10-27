package com.lira.vmest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AvatarCropPolicyTest {
    @Test
    public void portraitCropCentersHorizontallyBeforeScaling() {
        AvatarCropPolicy.Crop crop = AvatarCropPolicy.centeredSquare(400, 300);
        assertEquals(50, crop.left);
        assertEquals(0, crop.top);
        assertEquals(300, crop.side);
        assertEquals(0.48f, AvatarCropPolicy.scaleToTarget(crop.side, 144), 0.0001f);
    }

    @Test
    public void landscapeCropCentersVerticallyBeforeScaling() {
        AvatarCropPolicy.Crop crop = AvatarCropPolicy.centeredSquare(300, 400);
        assertEquals(0, crop.left);
        assertEquals(50, crop.top);
        assertEquals(300, crop.side);
        assertEquals(0.24f, AvatarCropPolicy.scaleToTarget(crop.side, 72), 0.0001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyImages() {
        AvatarCropPolicy.centeredSquare(0, 100);
    }
}
