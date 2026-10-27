package com.lira.vmest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BoardGameRulesTest {
    @Test public void checkersPerspectivePutsEachPlayersSideAtTheBottom() {
        assertEquals(0, BoardGameRules.checkersBoardIndexForView(0, 0, false));
        assertEquals(63, BoardGameRules.checkersBoardIndexForView(0, 0, true));
        assertEquals(0, BoardGameRules.checkersBoardIndexForView(7, 7, true));
        assertEquals(1, BoardGameRules.checkersBoardIndexForView(7, 6, true));
        assertEquals(57, BoardGameRules.checkersBoardIndexForView(7, 1, false));
        assertEquals(-1, BoardGameRules.checkersBoardIndexForView(8, 0, false));
        assertEquals(-1, BoardGameRules.checkersBoardIndexForView(0, 8, true));
    }

    @Test public void checkersAssignsWhiteToSecondPlayerAndAllowsWhiteToMoveDown() {
        char[] cells = new char[64];
        java.util.Arrays.fill(cells, '.');
        cells[17] = 'r';
        cells[42] = 'b';
        String board = new String(cells);

        java.util.Map<Integer, java.util.List<Integer>> moves =
                BoardGameRules.legalCheckersMoves(board, "second", "first", "second", -1, false);
        assertTrue(moves.get(17).contains(24));
        assertTrue(moves.get(17).contains(26));
        assertFalse(moves.containsKey(42));

        BoardGameRules.CheckersMove result = BoardGameRules.moveCheckers(
                board, "second", "first", "second", "second", -1, 17, 24, false);
        assertEquals('r', result.board.charAt(24));
        assertEquals('.', result.board.charAt(17));
        assertEquals("first", result.turn);
    }

    @Test public void swappingCheckersColorsPreservesKingsAndBoardCoordinates() {
        char[] cells = new char[64];
        java.util.Arrays.fill(cells, '.');
        cells[8] = 'r';
        cells[17] = 'B';
        String swapped = BoardGameRules.swapCheckersColors(new String(cells));
        assertEquals('b', swapped.charAt(8));
        assertEquals('R', swapped.charAt(17));
        assertEquals('.', swapped.charAt(0));
    }

    @Test public void bothPlayersSeeTheirOwnCheckersOnTheNearSide() {
        char[] cells = new char[64];
        java.util.Arrays.fill(cells, '.');
        cells[57] = 'r';
        cells[1] = 'b';
        String normalBoard = new String(cells);
        assertEquals('r', normalBoard.charAt(
                BoardGameRules.checkersBoardIndexForView(7, 1, false)));
        assertEquals('b', normalBoard.charAt(
                BoardGameRules.checkersBoardIndexForView(7, 6, true)));

        String secondPlayerStarts = BoardGameRules.swapCheckersColors(normalBoard);
        assertEquals('b', secondPlayerStarts.charAt(
                BoardGameRules.checkersBoardIndexForView(7, 1, false)));
        assertEquals('r', secondPlayerStarts.charAt(
                BoardGameRules.checkersBoardIndexForView(7, 6, true)));
    }

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

    @Test public void dotsAndBoxesGivesStarterTheStandardMarkerAndScore() {
        char[] edges = new char[40];
        java.util.Arrays.fill(edges, '.');
        edges[0] = 'B';
        edges[4] = 'B';
        edges[20] = 'B';
        BoardGameRules.DotsMove result = BoardGameRules.moveDotsAndBoxes(
                new String(edges), "................", 21,
                "second", "first", "second", 0, 0, "second");
        assertEquals('A', result.edges.charAt(21));
        assertEquals('A', result.boxes.charAt(0));
        assertEquals(1, result.score1);
        assertEquals(0, result.score2);
    }
}
