package com.lira.vmest;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SharedLink {
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s<>]+");

    private SharedLink() { }

    static String firstHttpUrl(String text) {
        if (text == null) return "";
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group().replaceAll("[),.!?;:]+$", "");
            try {
                URI uri = URI.create(candidate);
                String scheme = uri.getScheme();
                if (uri.getHost() != null && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)))
                    return candidate;
            } catch (IllegalArgumentException ignored) { }
        }
        return "";
    }
}
