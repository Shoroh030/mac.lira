package com.lira.vmest;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

final class InviteQrCode {
    private InviteQrCode() { }

    static BitMatrix encode(String inviteCode, int size) throws WriterException {
        String code = InviteCode.normalize(inviteCode);
        if (code.isEmpty()) throw new IllegalArgumentException("Invalid invite code");
        if (size < 1) throw new IllegalArgumentException("QR size must be positive");
        return new MultiFormatWriter().encode(
                "VMESTE:" + code, BarcodeFormat.QR_CODE, size, size);
    }
}
