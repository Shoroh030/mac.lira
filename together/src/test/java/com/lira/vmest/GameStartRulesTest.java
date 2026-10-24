package com.lira.vmest;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class GameStartRulesTest {
    @Test public void firstMoveIsChosenFromTheTwoPlayers() {
        assertEquals(new Random(0).nextBoolean() ? "first" : "second",
                GameStartRules.chooseFirstPlayer("first", "second", new Random(0)));
        assertEquals(new Random(1).nextBoolean() ? "first" : "second",
                GameStartRules.chooseFirstPlayer("first", "second", new Random(1)));
        String selected=GameStartRules.chooseFirstPlayer("first", "second", new Random());
        assertTrue("first".equals(selected)||"second".equals(selected));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesToChooseUntilBothPlayersHaveJoined() {
        GameStartRules.chooseFirstPlayer("first", "", new Random());
    }

    @Test
    public void reusesPersistedChoiceInsteadOfMakingAnIndependentClientChoice() {
        assertEquals("second", GameStartRules.chooseOrKeepFirstPlayer(
                "first", "second", "second", new Random(0)));
    }

    @Test
    public void ticTacToeAssignsXToTheSharedFirstPlayerAndKeepsLegacyGamesReadable() {
        assertEquals('X', GameStartRules.ticTacToeMarkForPlayer("second", "second", "first"));
        assertEquals('O', GameStartRules.ticTacToeMarkForPlayer("first", "second", "first"));
        assertEquals('X', GameStartRules.ticTacToeMarkForPlayer("first", "", "first"));
        assertEquals('O', GameStartRules.ticTacToeMarkForPlayer("second", "", "first"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesTheSameAccountAsBothPlayers() {
        GameStartRules.chooseFirstPlayer("same", "same", new Random());
    }
}
