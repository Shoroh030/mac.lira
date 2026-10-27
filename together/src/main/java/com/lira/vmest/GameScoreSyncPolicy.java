package com.lira.vmest;

final class GameScoreSyncPolicy {
    private static final long INITIAL_RETRY_MILLIS = 5_000L;
    private static final long MAX_RETRY_MILLIS = 5 * 60_000L;

    private GameScoreSyncPolicy() { }

    static long retryDelayMillis(int failureCount) {
        int exponent = Math.max(0, Math.min(6, failureCount - 1));
        return Math.min(INITIAL_RETRY_MILLIS << exponent, MAX_RETRY_MILLIS);
    }

    static boolean belongsToCurrentAccount(GameScoreSync.Snapshot snapshot,
            String coupleId, String userId) {
        return snapshot != null
                && snapshot.coupleId.equals(coupleId)
                && snapshot.userId.equals(userId);
    }

    static boolean isIdempotentCompletionRetry(String requestedStatus, String currentStatus,
            String requestedState, String currentState) {
        return "finished".equals(requestedStatus)
                && "finished".equals(currentStatus)
                && requestedState != null
                && requestedState.equals(currentState);
    }
}
