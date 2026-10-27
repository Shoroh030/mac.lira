package com.lira.vmest;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GameRestartPolicyTest {
    @Test public void eitherParticipantCanRestartAnExistingGame() {
        assertTrue(GameRestartPolicy.canRestart("finished", "first", "second", "first"));
        assertTrue(GameRestartPolicy.canRestart("finished", "first", "second", "second"));
        assertTrue(GameRestartPolicy.canRestart("active", "first", "second", "first"));
        assertTrue(GameRestartPolicy.canRestart("waiting", "first", "second", "second"));
    }

    @Test public void restartIsUnavailableUntilBothPlayersHaveJoined() {
        assertFalse(GameRestartPolicy.canRestart("active", "first", "", "first"));
        assertFalse(GameRestartPolicy.canRestart("finished", "first", "", "first"));
        assertFalse(GameRestartPolicy.canRestart("closed", "first", "second", "first"));
    }

    @Test public void restartIsUnavailableToNonParticipants() {
        assertFalse(GameRestartPolicy.canRestart("finished", "first", "second", "outsider"));
        assertFalse(GameRestartPolicy.canRestart("finished", "first", "second", ""));
    }
}
