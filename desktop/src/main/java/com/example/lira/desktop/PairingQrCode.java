package com.example.lira.desktop;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.util.EnumMap;
import java.util.Map;

final class PairingQrCode {
    private PairingQrCode() {
    }

    static BitMatrix encode(String address, String token) throws WriterException {
        String contents = "LIRA|1|" + address + "|" + SyncServer.PORT + "|" + token;
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 2);
        return new MultiFormatWriter().encode(contents, BarcodeFormat.QR_CODE, 320, 320, hints);
    }

    static String contents(String address, String token) {
        return "LIRA|1|" + address + "|" + SyncServer.PORT + "|" + token;
    }
}
