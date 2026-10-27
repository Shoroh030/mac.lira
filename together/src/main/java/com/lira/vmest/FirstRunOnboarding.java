package com.lira.vmest;

final class FirstRunOnboarding {
    private FirstRunOnboarding() { }

    static boolean shouldShow(boolean signedIn, boolean completed, boolean incomingShare) {
        return !signedIn && !completed && !incomingShare;
    }
}
