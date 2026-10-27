package com.lira.vmest;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.Locale;

final class GiftPreviewUrl {
    private GiftPreviewUrl() { }

    static String normalizePublicHttpUrl(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        try {
            URL url = new URL(value.trim());
            String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
            if (!"http".equals(protocol) && !"https".equals(protocol)) return "";
            if (url.getUserInfo() != null || url.getPort() < -1 || url.getPort() > 65535) return "";

            String host = IDN.toASCII(url.getHost()).toLowerCase(Locale.ROOT);
            String checkedHost = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
            if (!isPublicDnsName(checkedHost)) return "";
            return url.toExternalForm();
        } catch (Exception ignored) {
            return "";
        }
    }

    static boolean resolvesToPublicAddresses(String value) {
        String safeUrl = normalizePublicHttpUrl(value);
        if (safeUrl.isEmpty()) return false;
        try {
            String host = new URL(safeUrl).getHost();
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) return false;
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isPublicDnsName(String host) {
        if (host.length() < 3 || host.length() > 253 || host.indexOf('.') < 1 || host.indexOf(':') >= 0) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        String[] blockedSuffixes = {
                ".localhost", ".local", ".internal", ".home", ".lan",
                ".test", ".example", ".invalid", ".onion"
        };
        if ("localhost".equals(lower)) return false;
        for (String suffix : blockedSuffixes) {
            if (lower.endsWith(suffix)) return false;
        }

        String[] labels = lower.split("\\.", -1);
        if (looksLikeNumericAddress(labels)) return false;
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63
                    || label.startsWith("-") || label.endsWith("-")
                    || !label.matches("[a-z0-9-]+")) {
                return false;
            }
        }
        if (!labels[labels.length - 1].matches(".*[a-z].*")) return false;
        return true;
    }

    private static boolean looksLikeNumericAddress(String[] labels) {
        for (String label : labels) {
            if (!label.matches("(?i)([0-9]+|0x[0-9a-f]+)")) return false;
        }
        return true;
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            int third = bytes[2] & 0xff;
            if (first == 0 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0 && third == 0)
                    || (first == 192 && second == 0 && third == 2)
                    || (first == 192 && second == 88 && third == 99)
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && third == 100)
                    || (first == 203 && second == 0 && third == 113)) return false;
            return true;
        }
        if (address instanceof Inet6Address) {
            if ((bytes[0] & 0xe0) != 0x20) return false;
            if ((bytes[0] & 0xfe) == 0xfc) return false;
            if ((bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xc0) == 0x80) return false;
            if ((bytes[0] & 0xff) == 0x20 && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8) return false;
            return true;
        }
        return false;
    }
}
