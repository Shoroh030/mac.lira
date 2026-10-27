package com.lira.vmest;

public final class CoinSeriesRules {
    private CoinSeriesRules() {}

    public static boolean isSupportedTarget(int wins) {
        return wins == 1 || wins == 3 || wins == 6 || wins == 10;
    }

    public static Score advance(int firstWins, int secondWins, int targetWins, int roundWinner) {
        if (!isSupportedTarget(targetWins)) throw new IllegalArgumentException("Unsupported series target");
        if (roundWinner != 0 && roundWinner != 1) throw new IllegalArgumentException("Unknown round winner");
        int first = firstWins + (roundWinner == 0 ? 1 : 0);
        int second = secondWins + (roundWinner == 1 ? 1 : 0);
        String winner = first >= targetWins ? "first" : second >= targetWins ? "second" : "";
        return new Score(first, second, winner);
    }

    public static final class Score {
        public final int firstWins;
        public final int secondWins;
        public final String winner;

        private Score(int firstWins, int secondWins, String winner) {
            this.firstWins = firstWins;
            this.secondWins = secondWins;
            this.winner = winner;
        }
    }
}
