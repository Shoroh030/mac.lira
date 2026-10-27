package com.lira.vmest;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class InviteQrCodeTest {
    @Test
    public void encodedInviteQrCanBeScannedBack() throws Exception {
        BitMatrix matrix = InviteQrCode.encode("a1b2c3d4", 256);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        Result decoded = new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(source)));
        assertEquals("VMESTE:A1B2C3D4", decoded.getText());
        assertEquals("A1B2C3D4", InviteCode.normalize(decoded.getText()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidInviteCodeBeforeEncoding() throws Exception {
        InviteQrCode.encode("too-short", 256);
    }
}
