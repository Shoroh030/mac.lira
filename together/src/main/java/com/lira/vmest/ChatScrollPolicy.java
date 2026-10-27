package com.lira.vmest;

public final class ChatScrollPolicy {
    private boolean followingLatest = true;
    private int pendingIncoming;

    public boolean onScroll(int scrollY, int viewportHeight, int contentHeight, int bottomThreshold) {
        followingLatest = scrollY + viewportHeight >= contentHeight - bottomThreshold;
        if (followingLatest) pendingIncoming = 0;
        return followingLatest;
    }

    public boolean onMessagesAdded(int incomingCount, boolean ownMessage) {
        if (ownMessage) {
            jumpToLatest();
            return true;
        }
        if (incomingCount <= 0) return followingLatest;
        if (followingLatest) {
            pendingIncoming = 0;
            return true;
        }
        pendingIncoming += incomingCount;
        return false;
    }

    public void jumpToLatest() {
        followingLatest = true;
        pendingIncoming = 0;
    }

    public boolean isFollowingLatest() {
        return followingLatest;
    }

    public int pendingIncomingCount() {
        return pendingIncoming;
    }
}
