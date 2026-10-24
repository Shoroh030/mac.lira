package com.lira.vmest;

import java.net.URI;
import java.net.URISyntaxException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Locale;

final class WatchPlayerSource {
    enum Kind {
        YOUTUBE,
        DIRECT_VIDEO,
        WEBSITE
    }

    final Kind kind;
    final String url;
    final String youtubeVideoId;

    private WatchPlayerSource(Kind kind, String url, String youtubeVideoId) {
        this.kind = kind;
        this.url = url;
        this.youtubeVideoId = youtubeVideoId;
    }

    static WatchPlayerSource parse(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Введите HTTPS-ссылку");
        }

        String url = rawUrl.trim();
        if (url.length() > 2_048) {
            throw new IllegalArgumentException("Ссылка слишком длинная");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Некорректная ссылка", e);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Для просмотра поддерживаются только безопасные HTTPS-ссылки");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (isYouTubeHost(normalizedHost)) {
            String videoId = youtubeVideoId(uri, normalizedHost);
            if (videoId != null) {
                return new WatchPlayerSource(Kind.YOUTUBE, uri.toASCIIString(), videoId);
            }
        }

        String path = uri.getPath();
        String lowerPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".mp4") || lowerPath.endsWith(".m3u8")
                || lowerPath.endsWith(".webm")) {
            return new WatchPlayerSource(Kind.DIRECT_VIDEO, uri.toASCIIString(), null);
        }
        return new WatchPlayerSource(Kind.WEBSITE, uri.toASCIIString(), null);
    }

    private static boolean isYouTubeHost(String host) {
        return host.equals("youtu.be")
                || host.equals("youtube.com")
                || host.endsWith(".youtube.com")
                || host.equals("youtube-nocookie.com")
                || host.endsWith(".youtube-nocookie.com");
    }

    private static String youtubeVideoId(URI uri, String host) {
        String candidate = null;
        if (host.equals("youtu.be")) {
            candidate = firstPathSegment(uri.getPath());
        } else {
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.equals("/watch")) {
                candidate = queryParameter(uri.getRawQuery(), "v");
            } else {
                String[] segments = path.split("/");
                if (segments.length >= 3 && ("embed".equals(segments[1])
                        || "shorts".equals(segments[1]) || "live".equals(segments[1]))) {
                    candidate = segments[2];
                }
            }
        }
        return candidate != null && candidate.matches("[A-Za-z0-9_-]{11}") ? candidate : null;
    }

    private static String firstPathSegment(String path) {
        if (path == null) return null;
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) return segment;
        }
        return null;
    }

    private static String queryParameter(String query, String name) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && name.equals(parts[0])) {
                try {
                    return URLDecoder.decode(parts[1], "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
