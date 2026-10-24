package com.lira.vmest;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class GameScoreRulesTest {
    @Test
    public void countsWinsAndDrawsAcrossDifferentGames() {
        GameScoreRules.Summary score = GameScoreRules.summarize(Arrays.asList(
                new GameScoreRules.Outcome("one", "one", "two"),
                new GameScoreRules.Outcome("two", "two", "one"),
                new GameScoreRules.Outcome("draw", "one", "two"),
                new GameScoreRules.Outcome("one", "one", "two")),
                "one", "two");

        assertEquals(2, score.playerWins);
        assertEquals(1, score.partnerWins);
        assertEquals(1, score.draws);
    }

    @Test
    public void usesKnownPartnerIdWhenLegacyOutcomeHasNoPlayerIds() {
        GameScoreRules.Summary score = GameScoreRules.summarize(Collections.singletonList(
                new GameScoreRules.Outcome("two", "", "")), "one", "two");

        assertEquals(0, score.playerWins);
        assertEquals(1, score.partnerWins);
    }

    @Test
    public void ignoresOutcomesThatDoNotBelongToThePair() {
        GameScoreRules.Summary score = GameScoreRules.summarize(Collections.singletonList(
                new GameScoreRules.Outcome("other", "other", "someone-else")),
                "one", "two");

        assertEquals(0, score.playerWins);
        assertEquals(0, score.partnerWins);
        assertEquals(0, score.draws);
    }
}
