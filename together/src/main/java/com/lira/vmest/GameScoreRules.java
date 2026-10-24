package com.lira.vmest;

import java.util.List;

final class GameScoreRules {
    private GameScoreRules() { }

    static Summary summarize(List<Outcome> outcomes, String playerId, String partnerId) {
        int playerWins = 0;
        int partnerWins = 0;
        int draws = 0;
        for (Outcome outcome : outcomes) {
            if ("draw".equals(outcome.winner)) {
                draws++;
            } else if (playerId.equals(outcome.winner)) {
                playerWins++;
            } else {
                String opponentId = playerId.equals(outcome.playerOne)
                        ? outcome.playerTwo
                        : playerId.equals(outcome.playerTwo) ? outcome.playerOne : partnerId;
                if (opponentId != null && opponentId.equals(outcome.winner)) partnerWins++;
            }
        }
        return new Summary(playerWins, partnerWins, draws);
    }

    static final class Outcome {
        final String winner;
        final String playerOne;
        final String playerTwo;

        Outcome(String winner, String playerOne, String playerTwo) {
            this.winner = winner == null ? "" : winner;
            this.playerOne = playerOne == null ? "" : playerOne;
            this.playerTwo = playerTwo == null ? "" : playerTwo;
        }
    }

    static final class Summary {
        final int playerWins;
        final int partnerWins;
        final int draws;

        Summary(int playerWins, int partnerWins, int draws) {
            this.playerWins = playerWins;
            this.partnerWins = partnerWins;
            this.draws = draws;
        }
    }
}
