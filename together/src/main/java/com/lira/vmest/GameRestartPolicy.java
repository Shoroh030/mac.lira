package com.lira.vmest;

final class GameRestartPolicy {
    private GameRestartPolicy() {}

    static boolean canRestart(String status, String playerOne, String playerTwo, String userId) {
        return ("waiting".equals(status) || "active".equals(status) || "finished".equals(status))
                && playerOne != null && !playerOne.isEmpty()
                && playerTwo != null && !playerTwo.isEmpty()
                && userId != null
                && (userId.equals(playerOne) || userId.equals(playerTwo));
    }
}
