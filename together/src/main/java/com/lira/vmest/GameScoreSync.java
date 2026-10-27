package com.lira.vmest;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

final class GameScoreSync {
    private static final String CACHE_PREFIX = "game_score_snapshot_";

    private final PairGameSync games;
    private final SharedPreferences prefs;

    GameScoreSync(Context context) {
        Context appContext = context.getApplicationContext();
        games = new PairGameSync(appContext);
        prefs = appContext.getSharedPreferences("vmeste_data", Context.MODE_PRIVATE);
    }

    Snapshot refresh() throws Exception {
        if (!games.ready()) throw new IllegalStateException("Pair account is not ready");
        String coupleId = games.coupleId();
        String userId = games.userId();
        String partnerId = prefs.getString("partner_user_id", "");
        Snapshot snapshot = summarize(games.history(), coupleId, userId, partnerId);
        if (!coupleId.equals(games.coupleId()) || !userId.equals(games.userId())) {
            throw new IllegalStateException("Account changed during score sync");
        }
        prefs.edit().putString(cacheKey(coupleId, userId), snapshot.toJson().toString()).apply();
        return snapshot;
    }

    static Snapshot cached(Context context, String coupleId, String userId) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences("vmeste_data", Context.MODE_PRIVATE);
        String serialized = prefs.getString(cacheKey(coupleId, userId), "");
        if (serialized.isEmpty()) return null;
        try {
            return Snapshot.fromJson(new JSONObject(serialized));
        } catch (JSONException e) {
            prefs.edit().remove(cacheKey(coupleId, userId)).apply();
            return null;
        }
    }

    static Snapshot summarize(JSONArray rows, String coupleId, String userId, String partnerId) {
        ArrayList<GameRecord> records = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            JSONObject state = row.optJSONObject("state");
            if (state == null) continue;
            records.add(new GameRecord(row.optString("id", ""), row.optString("status"),
                    state.optString("winner"), state.optString("p1"), state.optString("p2")));
        }
        return summarize(records, coupleId, userId, partnerId);
    }

    static Snapshot summarize(List<GameRecord> records, String coupleId,
            String userId, String partnerId) {
        HashSet<String> seen = new HashSet<>();
        ArrayList<GameScoreRules.Outcome> outcomes = new ArrayList<>();
        for (GameRecord record : records) {
            if (!"finished".equals(record.status)
                    || record.id.isEmpty() || !seen.add(record.id)) continue;
            outcomes.add(new GameScoreRules.Outcome(record.winner, record.playerOne, record.playerTwo));
        }
        GameScoreRules.Summary summary = GameScoreRules.summarize(outcomes, userId, partnerId);
        return new Snapshot(coupleId, userId, summary.playerWins, summary.partnerWins,
                summary.draws, System.currentTimeMillis());
    }

    private static String cacheKey(String coupleId, String userId) {
        return CACHE_PREFIX + coupleId + "_" + userId;
    }

    static final class Snapshot {
        final String coupleId;
        final String userId;
        final int playerWins;
        final int partnerWins;
        final int draws;
        final long syncedAt;

        Snapshot(String coupleId, String userId, int playerWins, int partnerWins,
                int draws, long syncedAt) {
            this.coupleId = coupleId;
            this.userId = userId;
            this.playerWins = playerWins;
            this.partnerWins = partnerWins;
            this.draws = draws;
            this.syncedAt = syncedAt;
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("couple_id", coupleId);
            json.put("user_id", userId);
            json.put("player_wins", playerWins);
            json.put("partner_wins", partnerWins);
            json.put("draws", draws);
            json.put("synced_at", syncedAt);
            return json;
        }

        static Snapshot fromJson(JSONObject json) {
            return new Snapshot(json.optString("couple_id"), json.optString("user_id"),
                    json.optInt("player_wins"), json.optInt("partner_wins"),
                    json.optInt("draws"), json.optLong("synced_at"));
        }
    }

    static final class GameRecord {
        final String id;
        final String status;
        final String winner;
        final String playerOne;
        final String playerTwo;

        GameRecord(String id, String status, String winner, String playerOne, String playerTwo) {
            this.id = id == null ? "" : id;
            this.status = status == null ? "" : status;
            this.winner = winner == null ? "" : winner;
            this.playerOne = playerOne == null ? "" : playerOne;
            this.playerTwo = playerTwo == null ? "" : playerTwo;
        }
    }
}
