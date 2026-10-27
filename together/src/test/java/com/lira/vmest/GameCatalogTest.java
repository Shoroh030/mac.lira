package com.lira.vmest;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GameCatalogTest {
    @Test
    public void bothGamesSurfacesUseOnlyTheSixSupportedGames() {
        Set<String> ids = new HashSet<>();
        for (GameCatalog.Entry game : GameCatalog.entries()) ids.add(game.id);

        assertEquals(6, ids.size());
        assertTrue(ids.contains("tic_tac_toe"));
        assertTrue(ids.contains("sea_battle"));
        assertTrue(ids.contains("durak"));
        assertTrue(ids.contains("checkers"));
        assertTrue(ids.contains("connect_four"));
        assertTrue(ids.contains("dots_boxes"));
        assertFalse(ids.contains("couple_quiz"));
        assertFalse(ids.contains("guess_tune"));
    }
}
