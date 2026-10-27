package com.example.lira.desktop;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.RGBLuminanceSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PairingQrCodeTest {
    @Test
    void generatedQrContainsThePhonePairingPayload() throws Exception {
        String address = "192.168.1.20";
        String token = "01234567";
        var matrix = PairingQrCode.encode(address, token);
        int[] pixels = new int[matrix.getWidth() * matrix.getHeight()];
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                pixels[y * matrix.getWidth() + x] = matrix.get(x, y)
                        ? 0xff000000 : 0xffffffff;
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new RGBLuminanceSource(
                        matrix.getWidth(), matrix.getHeight(), pixels)));

        assertEquals(PairingQrCode.contents(address, token),
                new MultiFormatReader().decode(bitmap).getText());
    }
}
