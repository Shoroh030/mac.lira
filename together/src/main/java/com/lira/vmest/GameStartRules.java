package com.lira.vmest;

import java.util.Random;

public final class GameStartRules {
    private GameStartRules() {}

    public static String chooseFirstPlayer(String first, String second, Random random) {
        if (first == null || first.isEmpty() || second == null || second.isEmpty()
                || first.equals(second) || random == null) {
            throw new IllegalArgumentException("Both players and a random source are required");
        }
        return random.nextBoolean() ? first : second;
    }

    static String chooseOrKeepFirstPlayer(
            String first, String second, String persistedFirstPlayer, Random random) {
        if (first == null || first.isEmpty() || second == null || second.isEmpty()
                || first.equals(second)) {
            throw new IllegalArgumentException("Two distinct players are required");
        }
        if (first != null && first.equals(persistedFirstPlayer)
                || second != null && second.equals(persistedFirstPlayer)) {
            return persistedFirstPlayer;
        }
        return chooseFirstPlayer(first, second, random);
    }

    static char ticTacToeMarkForPlayer(String player, String firstPlayer, String playerOne) {
        String xPlayer = firstPlayer == null || firstPlayer.isEmpty() ? playerOne : firstPlayer;
        return player != null && player.equals(xPlayer) ? 'X' : 'O';
    }
}
