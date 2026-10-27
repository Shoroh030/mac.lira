package com.lira.vmest;

final class SharedTimerSyncPolicy {
    private SharedTimerSyncPolicy() { }

    static boolean shouldClearLocalTimer(boolean remoteTimerExists, boolean pendingUpload) {
        return !remoteTimerExists && !pendingUpload;
    }

    static boolean shouldRenderAfterSync(String before, String after, boolean editing) {
        return !editing && !before.equals(after);
    }
}
