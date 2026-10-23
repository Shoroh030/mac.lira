package com.lira.vmest;

final class ReleaseVersion {
    private ReleaseVersion() {}

    static boolean isValid(String version) {
        if (version == null || !version.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?")) return false;
        try {
            for (String part : version.split("\\.")) Integer.parseInt(part);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static int compare(String left, String right) {
        if (!isValid(left) || !isValid(right)) throw new IllegalArgumentException("Неверный формат версии");
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < 3; i++) {
            int x = i < a.length ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }
}
