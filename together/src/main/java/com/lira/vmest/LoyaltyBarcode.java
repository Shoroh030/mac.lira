package com.lira.vmest;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

final class LoyaltyBarcode {
    private static final List<BarcodeFormat> FORMATS = Collections.unmodifiableList(Arrays.asList(
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.ITF,
            BarcodeFormat.CODABAR,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX
    ));

    private LoyaltyBarcode() { }

    static List<BarcodeFormat> formats() {
        return FORMATS;
    }

    static boolean supports(String formatName) {
        if (formatName == null) return false;
        try {
            return FORMATS.contains(BarcodeFormat.valueOf(formatName.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String validate(String value, String formatName) {
        String content = value == null ? "" : value.trim();
        if (content.isEmpty()) throw new IllegalArgumentException("Введите код карты");
        if (content.length() > 512) throw new IllegalArgumentException("Код слишком длинный (максимум 512 символов)");
        for (int i = 0; i < content.length(); i++) {
            if (Character.isISOControl(content.charAt(i))) {
                throw new IllegalArgumentException("Код содержит неподдерживаемые символы");
            }
        }
        if (!supports(formatName)) throw new IllegalArgumentException("Этот формат карты пока не поддерживается");
        return content;
    }

    static BitMatrix encode(String value, String formatName, int width, int height) throws WriterException {
        String content = validate(value, formatName);
        BarcodeFormat format = BarcodeFormat.valueOf(formatName.trim().toUpperCase(java.util.Locale.ROOT));
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN,
                format == BarcodeFormat.QR_CODE || format == BarcodeFormat.DATA_MATRIX ? 4 : 10);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        return new MultiFormatWriter().encode(content, format, width, height, hints);
    }
}
