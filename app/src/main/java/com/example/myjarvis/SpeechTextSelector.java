package com.example.myjarvis;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

final class SpeechTextSelector {
    private SpeechTextSelector() {
    }

    static String select(JSONObject result) throws JSONException {
        String fallback = result.optString("text", "").trim();
        JSONArray alternatives = result.optJSONArray("alternatives");
        if (alternatives == null || alternatives.length() == 0) {
            return fallback;
        }

        String bestText = fallback;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < alternatives.length(); index++) {
            JSONObject alternative = alternatives.optJSONObject(index);
            if (alternative == null) {
                continue;
            }
            String candidate = alternative.optString("text", "").trim();
            if (candidate.isEmpty()) {
                continue;
            }
            double score = alternative.optDouble("confidence", 0.0);
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (ScreenReadCommand.matches(candidate)) {
                score += 0.35;
            } else if (AndroidClockCommands.parse(candidate) != null) {
                score += 0.30;
            } else if (PcCommandClient.parse(candidate) != null) {
                score += 0.25;
            } else if (startsWithCommand(lower, "открой", "открыть", "откройте",
                    "запусти", "запустить", "запустите", "open", "launch", "start", "run",
                    "найди", "поищи", "search")) {
                score += 0.15;
            }
            if (score > bestScore) {
                bestScore = score;
                bestText = candidate;
            }
        }
        return bestText;
    }

    private static boolean startsWithCommand(String text, String... commands) {
        for (String command : commands) {
            if (text.equals(command) || text.startsWith(command + " ")) {
                return true;
            }
        }
        return false;
    }
}
