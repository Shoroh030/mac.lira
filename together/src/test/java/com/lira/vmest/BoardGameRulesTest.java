package com.lira.vmest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BoardGameRulesTest {
    @Test public void connectFourUsesGravityAndFindsWin() {
        String board = "..........................................";
        assertEquals(35, BoardGameRules.connectFourLandingRow(board, 0));
        String winning = "..........................................";
        StringBuilder state = new StringBuilder(winning);
        for (int row = 0; row < 6; row++) state.setCharAt(row * 7, 'R');
        state.setCharAt(35, 'R');
        state.setCharAt(36, 'R');
        state.setCharAt(37, 'R');
        state.setCharAt(38, 'R');
        assertTrue(BoardGameRules.hasConnectFour(state.toString(), 'R'));
        assertEquals(-1, BoardGameRules.connectFourLandingRow(state.toString(), 0));
    }

    @Test public void checkersRequiresCaptureAndCompletesGame() {
        char[] cells = new char[64];
        java.util.Arrays.fill(cells, '.');
        cells[27] = 'r';
        cells[18] = 'b';
        cells[45] = 'r';
        java.util.Map<Integer, java.util.List<Integer>> legal =
                BoardGameRules.legalCheckersMoves(new String(cells), "one", "one", -1);
        assertEquals(1, legal.size());
        assertTrue(legal.get(27).contains(9));
        assertFalse(legal.containsKey(45));

        BoardGameRules.CheckersMove result = BoardGameRules.moveCheckers(
                new String(cells), "one", "one", "two", -1, 27, 9);
        assertEquals('r', result.board.charAt(9));
        assertEquals('.', result.board.charAt(18));
        assertEquals("one", result.winner);
    }

    @Test public void checkersPromotesAndChainsCaptures() {
        char[] cells = new char[64];
        java.util.Arrays.fill(cells, '.');
        cells[18] = 'r';
        cells[9] = 'b';
        cells[27] = 'b';
        BoardGameRules.CheckersMove result = BoardGameRules.moveCheckers(
                new String(cells), "one", "one", "two", -1, 18, 0);
        assertEquals('R', result.board.charAt(0));
        assertEquals(0, result.chain);
        assertTrue(BoardGameRules.legalCheckersMoves(result.board, "one", "one", result.chain).get(0).contains(36));
        BoardGameRules.CheckersMove chained = BoardGameRules.moveCheckers(
                result.board, "one", "one", "two", result.chain, 0, 36);
        assertEquals('.', chained.board.charAt(27));
        assertEquals("one", chained.winner);
    }

    @Test public void flyingKingQuietMoveDoesNotBecomeCapture() {
        char[] cells = new char[64];
        java.util.Arrays.fill(cells, '.');
        cells[0] = 'R';
        cells[40] = 'b';
        BoardGameRules.CheckersMove result = BoardGameRules.moveCheckers(
                new String(cells), "one", "one", "two", -1, 0, 36);
        assertEquals("two", result.turn);
        assertEquals("", result.winner);
    }

    @Test public void dotsAndBoxesAwardsCompletedSquareAndKeepsTurn() {
        char[] edges = new char[40];
        java.util.Arrays.fill(edges, '.');
        edges[0] = 'A';
        edges[4] = 'B';
        edges[20] = 'A';
        BoardGameRules.DotsMove result = BoardGameRules.moveDotsAndBoxes(
                new String(edges), "................", 21, "one", "one", "two", 0, 0);
        assertEquals('A', result.boxes.charAt(0));
        assertEquals(1, result.score1);
        assertEquals("one", result.turn);
        assertEquals("", result.winner);
    }
}
