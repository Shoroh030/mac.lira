package com.lira.vmest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CoinSeriesRulesTest {
    @Test public void supportsRequestedSeriesTargets() {
        assertTrue(CoinSeriesRules.isSupportedTarget(1));
        assertTrue(CoinSeriesRules.isSupportedTarget(3));
        assertTrue(CoinSeriesRules.isSupportedTarget(6));
        assertTrue(CoinSeriesRules.isSupportedTarget(10));
        assertFalse(CoinSeriesRules.isSupportedTarget(2));
    }

    @Test public void advancesScoreAndEndsAtConfiguredWins() {
        CoinSeriesRules.Score firstRound = CoinSeriesRules.advance(2, 1, 3, 0);
        assertEquals(3, firstRound.firstWins);
        assertEquals(1, firstRound.secondWins);
        assertEquals("first", firstRound.winner);

        CoinSeriesRules.Score secondRound = CoinSeriesRules.advance(2, 1, 3, 1);
        assertEquals(2, secondRound.firstWins);
        assertEquals(2, secondRound.secondWins);
        assertEquals("", secondRound.winner);
    }
}
