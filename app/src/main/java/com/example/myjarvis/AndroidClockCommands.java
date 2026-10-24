package com.example.myjarvis;

import android.content.Intent;
import android.provider.AlarmClock;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AndroidClockCommands {
    private static final Pattern DURATION = Pattern.compile(
            "(\\d+)\\s*(час(?:а|ов)?|ч|hours?|hrs?|минут(?:а|ы)?|мин|"
                    + "minutes?|mins?|секунд(?:а|ы)?|сек|seconds?|secs?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CLOCK_TIME = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3])[:.]([0-5]\\d)(?!\\d)");
    private static final Pattern SPACED_TIME = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3])\\s+([0-5]?\\d)(?!\\d)");
    private static final Pattern SPOKEN_TIME = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3])\\s*(?:час(?:а|ов)?|ч)"
                    + "(?:\\s*(?:и\\s*)?([0-5]?\\d)\\s*(?:минут(?:а|ы)?|мин)?)?");
    private static final Pattern HOUR_ONLY = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3])(?!\\d)");
    private static final Pattern COMPOUND_NUMBER = Pattern.compile(
            "(?iu)\\b(двадцать|тридцать|сорок|пятьдесят|шестьдесят)"
                    + "(?:\\s+и?\\s*(один|одна|одну|одно|два|две|три|четыре|пять|"
                    + "шесть|семь|восемь|девять))?\\b");
    private static final Pattern SINGLE_NUMBER = Pattern.compile(
            "(?iu)\\b(ноль|один|одна|одну|одно|два|две|три|четыре|пять|шесть|"
                    + "семь|восемь|девять|десять|одиннадцать|двенадцать|"
                    + "тринадцать|четырнадцать|пятнадцать|шестнадцать|"
                    + "семнадцать|восемнадцать|девятнадцать|двадцать|тридцать|"
                    + "сорок|пятьдесят|шестьдесят)\\b");

    private AndroidClockCommands() {
    }

    static Result parse(String utterance) {
        String text = normalizeSpokenNumbers(utterance == null ? "" : utterance.trim());
        String lower = text.toLowerCase(Locale.ROOT);
        String timerRemainder = removePrefix(lower, text,
                "поставь таймер", "установи таймер", "запусти таймер", "таймер",
                "set a timer", "set timer", "start a timer", "start timer", "timer");
        if (timerRemainder != null) {
            return timer(timerRemainder);
        }
        String alarmRemainder = removePrefix(lower, text,
                "поставь будильник", "установи будильник", "заведи будильник", "будильник",
                "set an alarm", "set alarm", "alarm");
        if (alarmRemainder != null) {
            return alarm(alarmRemainder);
        }
        return null;
    }

    private static String removePrefix(String lower, String original, String... prefixes) {
        for (String prefix : prefixes) {
            if (lower.equals(prefix) || lower.startsWith(prefix + " ")) {
                return original.substring(prefix.length()).trim()
                        .replaceFirst("(?iu)^(?:на|через|в|for|in|after)\\s+", "");
            }
        }
        return null;
    }

    private static Result timer(String value) {
        Matcher matcher = DURATION.matcher(value);
        long seconds = 0;
        boolean found = false;
        while (matcher.find()) {
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException exception) {
                return new Result("Укажи время таймера числами и единицами времени.", null);
            }
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            long multiplier = unit.startsWith("ч") || unit.startsWith("hour")
                    || unit.startsWith("hr") ? 3600L
                    : unit.startsWith("мин") || unit.startsWith("min") ? 60L : 1L;
            if (amount > (7L * 24 * 60 * 60 - seconds) / multiplier) {
                return new Result("Таймер можно поставить максимум на 7 дней.", null);
            }
            seconds += amount * multiplier;
            found = true;
        }
        if (!found || seconds < 1) {
            return new Result("Назови длительность, например: «поставь таймер на 5 минут».", null);
        }
        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, (int) seconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Таймер L.I.R.A.")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        return new Result("Открываю «Часы»: таймер на " + formatDuration(seconds)
                + " готов к запуску.", intent);
    }

    private static Result alarm(String value) {
        Matcher matcher = CLOCK_TIME.matcher(value);
        boolean found = matcher.find();
        if (!found) {
            matcher = SPOKEN_TIME.matcher(value);
            found = matcher.find();
        }
        if (!found) {
            matcher = SPACED_TIME.matcher(value);
            found = matcher.find();
        }
        if (!found) {
            matcher = HOUR_ONLY.matcher(value);
            found = matcher.find();
        }
        if (!found) {
            return new Result(
                    "Назови время будильника, например: «поставь будильник на 7:30».", null);
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.groupCount() < 2 || matcher.group(2) == null
                ? 0 : Integer.parseInt(matcher.group(2));
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Будильник L.I.R.A.")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        return new Result(String.format(Locale.ROOT,
                "Ставлю будильник на %02d:%02d.", hour, minute), intent);
    }

    private static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainingSeconds = seconds % 60;
        StringBuilder result = new StringBuilder();
        if (hours > 0) result.append(hours).append(" ч ");
        if (minutes > 0) result.append(minutes).append(" мин ");
        if (remainingSeconds > 0) result.append(remainingSeconds).append(" сек");
        return result.toString().trim();
    }

    private static String normalizeSpokenNumbers(String text) {
        Matcher compound = COMPOUND_NUMBER.matcher(text);
        StringBuffer compoundResult = new StringBuffer();
        while (compound.find()) {
            int number = numberValue(compound.group(1));
            if (compound.group(2) != null) {
                number += numberValue(compound.group(2));
            }
            compound.appendReplacement(compoundResult, Integer.toString(number));
        }
        compound.appendTail(compoundResult);

        Matcher single = SINGLE_NUMBER.matcher(compoundResult.toString());
        StringBuffer result = new StringBuffer();
        while (single.find()) {
            single.appendReplacement(result, Integer.toString(numberValue(single.group(1))));
        }
        single.appendTail(result);
        return result.toString();
    }

    private static int numberValue(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "ноль" -> 0;
            case "один", "одна", "одну", "одно" -> 1;
            case "два", "две" -> 2;
            case "три" -> 3;
            case "четыре" -> 4;
            case "пять" -> 5;
            case "шесть" -> 6;
            case "семь" -> 7;
            case "восемь" -> 8;
            case "девять" -> 9;
            case "десять" -> 10;
            case "одиннадцать" -> 11;
            case "двенадцать" -> 12;
            case "тринадцать" -> 13;
            case "четырнадцать" -> 14;
            case "пятнадцать" -> 15;
            case "шестнадцать" -> 16;
            case "семнадцать" -> 17;
            case "восемнадцать" -> 18;
            case "девятнадцать" -> 19;
            case "двадцать" -> 20;
            case "тридцать" -> 30;
            case "сорок" -> 40;
            case "пятьдесят" -> 50;
            case "шестьдесят" -> 60;
            default -> 0;
        };
    }

    static final class Result {
        final String message;
        final Intent intent;

        Result(String message, Intent intent) {
            this.message = message;
            this.intent = intent;
        }
    }
}
