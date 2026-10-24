package com.lira.vmest;

final class AvatarCachePolicy {
    private AvatarCachePolicy() { }

    static boolean shouldDownload(boolean sameRemotePath, boolean fileExists, long fileLength,
            boolean imageDecodable) {
        return !sameRemotePath || !fileExists || fileLength <= 0 || !imageDecodable;
    }
}
