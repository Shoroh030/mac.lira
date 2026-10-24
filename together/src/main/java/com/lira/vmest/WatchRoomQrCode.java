package com.lira.vmest;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

final class WatchRoomQrCode {
    private static final String PREFIX = "VMESTE:ROOM:";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern ROOM_CODE_PATTERN = Pattern.compile(
            "(?i)VMESTE:ROOM:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
    private static final Pattern ROOM_LINK_PATTERN = Pattern.compile(
            "(?i)vmeste://room/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/?");

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

    static String decodeInvitation(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        String roomId = decode(trimmed);
        if (roomId != null) return roomId;
        roomId = canonicalId(trimmed);
        if (roomId != null) return roomId;

        java.util.regex.Matcher link = ROOM_LINK_PATTERN.matcher(trimmed);
        if (link.find()) return canonicalId(link.group(1));
        java.util.regex.Matcher code = ROOM_CODE_PATTERN.matcher(trimmed);
        return code.find() ? canonicalId(code.group(1)) : null;
    }

    static String link(String roomId) {
        String canonicalId = canonicalId(roomId);
        if (canonicalId == null) throw new IllegalArgumentException("Invalid watch room ID");
        return "vmeste://room/" + canonicalId;
    }

    static String shareText(String roomId) {
        String canonicalId = canonicalId(roomId);
        if (canonicalId == null) throw new IllegalArgumentException("Invalid watch room ID");
        return "Подключайтесь к комнате просмотра «Вместе»:\n"
                + link(canonicalId) + "\nКод: " + PREFIX + canonicalId;
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
