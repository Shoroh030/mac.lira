package com.lira.vmest;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class LoyaltyBarcodeTest {
    @Test public void supportedFormatsAreExplicitAndUnsupportedFormatsAreRejected() {
        assertTrue(LoyaltyBarcode.supports("QR_CODE"));
        assertTrue(LoyaltyBarcode.supports("code_128"));
        assertFalse(LoyaltyBarcode.supports("PDF_417"));
        assertFalse(LoyaltyBarcode.supports(null));
    }

    @Test public void validatesTextAndFormatBeforeEncoding() {
        assertEquals("AB 123", LoyaltyBarcode.validate(" AB 123 ", "CODE_128"));
        assertThrows(IllegalArgumentException.class, () -> LoyaltyBarcode.validate("", "CODE_128"));
        assertThrows(IllegalArgumentException.class, () -> LoyaltyBarcode.validate("123", "PDF_417"));
        assertThrows(IllegalArgumentException.class, () -> LoyaltyBarcode.validate("A\nB", "QR_CODE"));
    }

    @Test public void code128RoundTripsThroughZxingDecoder() throws Exception {
        assertRoundTrip("PEL-012345", BarcodeFormat.CODE_128);
    }

    @Test public void qrRoundTripsThroughZxingDecoder() throws Exception {
        assertRoundTrip("https://example.test/loyalty/123", BarcodeFormat.QR_CODE);
    }

    @Test public void ean13RoundTripsThroughZxingDecoder() throws Exception {
        assertRoundTrip("5901234123457", BarcodeFormat.EAN_13);
    }

    private void assertRoundTrip(String contents, BarcodeFormat format) throws Exception {
        BitMatrix matrix = LoyaltyBarcode.encode(contents, format.name(), 900, 420);
        int[] pixels = new int[matrix.getWidth() * matrix.getHeight()];
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                pixels[y * matrix.getWidth() + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(matrix.getWidth(), matrix.getHeight(), pixels)));
        Result decoded = new MultiFormatReader().decode(bitmap);
        assertEquals(contents, decoded.getText());
        assertEquals(format, decoded.getBarcodeFormat());
    }
}
