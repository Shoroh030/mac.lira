package com.lira.vmest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BoardGameRules {
    static final class CheckersMove {
        final String board;
        final String turn;
        final String winner;
        final int chain;

        CheckersMove(String board, String turn, String winner, int chain) {
            this.board = board;
            this.turn = turn;
            this.winner = winner;
            this.chain = chain;
        }
    }

    static final class DotsMove {
        final String edges;
        final String boxes;
        final int score1;
        final int score2;
        final String turn;
        final String winner;

        DotsMove(String edges, String boxes, int score1, int score2, String turn, String winner) {
            this.edges = edges;
            this.boxes = boxes;
            this.score1 = score1;
            this.score2 = score2;
            this.turn = turn;
            this.winner = winner;
        }
    }

    private BoardGameRules() { }

    static int connectFourLandingRow(String board, int column) {
        if (board == null || board.length() != 42 || column < 0 || column >= 7) return -1;
        for (int row = 5; row >= 0; row--) {
            if (board.charAt(row * 7 + column) == '.') return row * 7 + column;
        }
        return -1;
    }

    static int checkersBoardIndexForView(int row, int column, boolean rotated) {
        if (row < 0 || row >= 8 || column < 0 || column >= 8) return -1;
        int boardRow = rotated ? 7 - row : row;
        int boardColumn = rotated ? 7 - column : column;
        return boardRow * 8 + boardColumn;
    }

    static boolean hasConnectFour(String board, char piece) {
        if (board == null || board.length() != 42) return false;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                int i = row * 7 + col;
                if (col + 3 < 7 && board.charAt(i) == piece && board.charAt(i + 1) == piece
                        && board.charAt(i + 2) == piece && board.charAt(i + 3) == piece) return true;
                if (row + 3 < 6 && board.charAt(i) == piece && board.charAt(i + 7) == piece
                        && board.charAt(i + 14) == piece && board.charAt(i + 21) == piece) return true;
                if (row + 3 < 6 && col + 3 < 7 && board.charAt(i) == piece && board.charAt(i + 8) == piece
                        && board.charAt(i + 16) == piece && board.charAt(i + 24) == piece) return true;
                if (row + 3 < 6 && col - 3 >= 0 && board.charAt(i) == piece && board.charAt(i + 6) == piece
                        && board.charAt(i + 12) == piece && board.charAt(i + 18) == piece) return true;
            }
        }
        return false;
    }

    static Map<Integer, List<Integer>> legalCheckersMoves(String board, String player, String playerOne, int chainFrom) {
        if (board == null || board.length() != 64 || player == null || player.isEmpty()) return new LinkedHashMap<>();
        char own = player.equals(playerOne) ? 'r' : 'b';
        boolean forced = chainFrom >= 0;
        Map<Integer, List<Integer>> captures = new LinkedHashMap<>();
        Map<Integer, List<Integer>> regular = new LinkedHashMap<>();
        for (int from = 0; from < 64; from++) {
            char piece = board.charAt(from);
            if (!isOwn(piece, own) || (forced && from != chainFrom)) continue;
            int row = from / 8, col = from % 8;
            boolean king = Character.isUpperCase(piece);
            int[][] directions = {{-1,-1},{-1,1},{1,-1},{1,1}};
            for (int[] direction : directions) {
                int dr = direction[0], dc = direction[1];
                if (king) {
                    addKingMoves(board, from, own, dr, dc, captures, regular);
                } else {
                    int stepRow = row + dr, stepCol = col + dc;
                    if (isInBoard(stepRow, stepCol) && board.charAt(stepRow * 8 + stepCol) == '.'
                            && !forced && dr == (own == 'r' ? -1 : 1)) {
                        addMove(regular, from, stepRow * 8 + stepCol);
                    }
                    int jumpRow = row + dr * 2, jumpCol = col + dc * 2;
                    if (isInBoard(jumpRow, jumpCol) && isOpponent(board.charAt(stepRow * 8 + stepCol), own)
                            && board.charAt(jumpRow * 8 + jumpCol) == '.') {
                        addMove(captures, from, jumpRow * 8 + jumpCol);
                    }
                }
            }
        }
        return !captures.isEmpty() || forced ? captures : regular;
    }

    static CheckersMove moveCheckers(String board, String player, String playerOne, String playerTwo,
                                     int chainFrom, int from, int to) {
        Map<Integer, List<Integer>> legal = legalCheckersMoves(board, player, playerOne, chainFrom);
        List<Integer> destinations = legal.get(from);
        if (destinations == null || !destinations.contains(to))
            throw new IllegalArgumentException("Недопустимый ход в шашках");

        char[] cells = board.toCharArray();
        char piece = cells[from];
        int fromRow = from / 8, fromCol = from % 8, toRow = to / 8, toCol = to % 8;
        boolean capture = false;
        int stepRow = Integer.signum(toRow - fromRow), stepCol = Integer.signum(toCol - fromCol);
        for (int r = fromRow + stepRow, c = fromCol + stepCol; r != toRow; r += stepRow, c += stepCol) {
            if (isOpponent(cells[r * 8 + c], pieceColor(piece))) {
                cells[r * 8 + c] = '.';
                capture = true;
                break;
            }
        }
        cells[from] = '.';
        if (piece == 'r' && toRow == 0) piece = 'R';
        else if (piece == 'b' && toRow == 7) piece = 'B';
        cells[to] = piece;

        String nextBoard = new String(cells);
        if (capture && !legalCheckersMoves(nextBoard, player, playerOne, to).isEmpty())
            return new CheckersMove(nextBoard, player, "", to);

        String nextPlayer = player.equals(playerOne) ? playerTwo : playerOne;
        char opponent = pieceColor(piece) == 'r' ? 'b' : 'r';
        boolean hasPieces = false;
        for (char cell : cells) if (isOwn(cell, opponent)) { hasPieces = true; break; }
        if (!hasPieces || legalCheckersMoves(nextBoard, nextPlayer, playerOne, -1).isEmpty())
            return new CheckersMove(nextBoard, player, player, -1);
        return new CheckersMove(nextBoard, nextPlayer, "", -1);
    }

    static DotsMove moveDotsAndBoxes(String edges, String boxes, int edge, String player,
                                     String playerOne, String playerTwo, int score1, int score2) {
        if (edges == null || edges.length() != 40 || boxes == null || boxes.length() != 16
                || edge < 0 || edge >= 40 || player == null || player.isEmpty()
                || playerTwo == null || playerTwo.isEmpty()
                || (!player.equals(playerOne) && !player.equals(playerTwo)))
            throw new IllegalArgumentException("Поле игры некорректно или ожидает второго игрока");
        if (edges.charAt(edge) != '.') throw new IllegalArgumentException("Эта линия уже занята");

        char marker = player.equals(playerOne) ? 'A' : 'B';
        char[] nextEdges = edges.toCharArray();
        char[] nextBoxes = boxes.toCharArray();
        nextEdges[edge] = marker;
        int completed = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                int box = row * 4 + col;
                if (nextBoxes[box] == '.' && nextEdges[row * 4 + col] != '.'
                        && nextEdges[(row + 1) * 4 + col] != '.'
                        && nextEdges[20 + row * 5 + col] != '.'
                        && nextEdges[20 + row * 5 + col + 1] != '.') {
                    nextBoxes[box] = marker;
                    completed++;
                }
            }
        }

        if (marker == 'A') score1 += completed; else score2 += completed;
        boolean finished = true;
        for (char value : nextEdges) if (value == '.') { finished = false; break; }
        String winner = "";
        if (finished) winner = score1 == score2 ? "draw" : score1 > score2 ? playerOne : playerTwo;
        String nextTurn = completed > 0 ? player : player.equals(playerOne) ? playerTwo : playerOne;
        return new DotsMove(new String(nextEdges), new String(nextBoxes), score1, score2,
                finished ? "" : nextTurn, winner);
    }

    private static void addKingMoves(String board, int from, char own, int dr, int dc,
                                     Map<Integer, List<Integer>> captures, Map<Integer, List<Integer>> regular) {
        int row = from / 8, col = from % 8;
        int r = row + dr, c = col + dc;
        while (isInBoard(r, c) && board.charAt(r * 8 + c) == '.') {
            addMove(regular, from, r * 8 + c);
            r += dr;
            c += dc;
        }
        if (!isInBoard(r, c) || !isOpponent(board.charAt(r * 8 + c), own)) return;
        r += dr;
        c += dc;
        while (isInBoard(r, c) && board.charAt(r * 8 + c) == '.') {
            addMove(captures, from, r * 8 + c);
            r += dr;
            c += dc;
        }
    }

    private static void addMove(Map<Integer, List<Integer>> moves, int from, int to) {
        List<Integer> destinations = moves.get(from);
        if (destinations == null) {
            destinations = new ArrayList<>();
            moves.put(from, destinations);
        }
        destinations.add(to);
    }

    private static boolean isInBoard(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    private static boolean isOwn(char piece, char color) {
        return piece == color || piece == Character.toUpperCase(color);
    }

    private static boolean isOpponent(char piece, char color) {
        return piece != '.' && !isOwn(piece, color);
    }

    private static char pieceColor(char piece) {
        return Character.toLowerCase(piece);
    }
}
