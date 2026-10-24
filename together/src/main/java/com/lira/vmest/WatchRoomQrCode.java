package com.lira.vmest;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

final class WatchRoomQrCode {
    private static final String PREFIX = "VMESTE:ROOM:";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private WatchRoomQrCode() { }

    static String encode(String roomId) {
        String canonicalId = canonicalId(roomId);
        if (canonicalId == null) throw new IllegalArgumentException("Invalid watch room ID");
        return PREFIX + canonicalId;
    }

    static String decode(String payload) {
        if (payload == null) return null;
        String value = payload.trim();
        if (!value.startsWith(PREFIX)) return null;
        return canonicalId(value.substring(PREFIX.length()));
    }

    private static String canonicalId(String value) {
        if (value == null || !UUID_PATTERN.matcher(value).matches()) return null;
        try {
            return UUID.fromString(value).toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
