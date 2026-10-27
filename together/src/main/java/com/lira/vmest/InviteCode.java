package com.lira.vmest;

import java.util.Locale;

final class InviteCode {
    private InviteCode() { }

    static String normalize(String value) {
        if (value == null) return "";
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (code.startsWith("VMESTE:")) code = code.substring("VMESTE:".length()).trim();
        return code.matches("[A-Z0-9]{8}") ? code : "";
    }
}
