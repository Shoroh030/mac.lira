package com.lira.vmest;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GameScoreSyncTest {
    @Test
    public void duplicateRowsFromRetriedPagesCountOnlyOnce() {
        GameScoreSync.GameRecord finished =
                game("game-1", "finished", "player", "player", "partner");
        GameScoreSync.Snapshot snapshot = GameScoreSync.summarize(Arrays.asList(
                finished, finished),
                "couple", "player", "partner");

        assertEquals(1, snapshot.playerWins);
        assertEquals(0, snapshot.partnerWins);
    }

    @Test
    public void onlyAuthoritativeFinishedRowsAffectTheScore() {
        GameScoreSync.Snapshot snapshot = GameScoreSync.summarize(Arrays.asList(
                game("finished", "finished", "partner", "player", "partner"),
                game("conflicted-active", "active", "player", "player", "partner"),
                game("missing-winner", "finished", "", "player", "partner")),
                "couple", "player", "partner");

        assertEquals(0, snapshot.playerWins);
        assertEquals(1, snapshot.partnerWins);
    }

    @Test
    public void cachedSnapshotCannotCrossAccountOrCoupleBoundary() {
        GameScoreSync.Snapshot snapshot =
                new GameScoreSync.Snapshot("couple-a", "user-a", 3, 2, 1, 100L);

        assertTrue(GameScoreSyncPolicy.belongsToCurrentAccount(snapshot, "couple-a", "user-a"));
        assertFalse(GameScoreSyncPolicy.belongsToCurrentAccount(snapshot, "couple-b", "user-a"));
        assertFalse(GameScoreSyncPolicy.belongsToCurrentAccount(snapshot, "couple-a", "user-b"));
    }

    @Test
    public void retryDelayUsesBoundedExponentialBackoff() {
        assertEquals(5_000L, GameScoreSyncPolicy.retryDelayMillis(1));
        assertEquals(10_000L, GameScoreSyncPolicy.retryDelayMillis(2));
        assertEquals(300_000L, GameScoreSyncPolicy.retryDelayMillis(100));
    }

    @Test
    public void repeatedCompletionIsIdempotentOnlyWhenFinalStateMatches() {
        String completedState = "{\"winner\":\"player\",\"board\":[1,2,0]}";
        String conflictingState = "{\"winner\":\"partner\",\"board\":[1,2,0]}";
        assertTrue(GameScoreSyncPolicy.isIdempotentCompletionRetry(
                "finished", "finished", completedState, completedState));
        assertFalse(GameScoreSyncPolicy.isIdempotentCompletionRetry(
                "finished", "finished", completedState, conflictingState));
        assertFalse(GameScoreSyncPolicy.isIdempotentCompletionRetry(
                "active", "finished", completedState, completedState));
        assertFalse(GameScoreSyncPolicy.isIdempotentCompletionRetry(
                "finished", "active", completedState, completedState));
    }

    private static GameScoreSync.GameRecord game(String id, String status, String winner,
            String playerOne, String playerTwo) {
        return new GameScoreSync.GameRecord(id, status, winner, playerOne, playerTwo);
    }
}
