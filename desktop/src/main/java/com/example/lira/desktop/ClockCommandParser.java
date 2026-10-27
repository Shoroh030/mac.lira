package com.example.lira.desktop;

import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ClockCommandParser {
    private static final Pattern DURATION = Pattern.compile(
            "(\\d+)\\s*(час(?:а|ов)?|ч|минут(?:а|ы)?|мин|секунд(?:а|ы)?|сек)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TIMER_DIGITAL = Pattern.compile(
            "(?<!\\d)(\\d{1,3}):([0-5]\\d)(?!\\d)");
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

    private ClockCommandParser() {
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

    static Command parse(String utterance) {
        String text = normalizeSpokenNumbers(utterance == null ? "" : utterance.trim());
        String lower = text.toLowerCase(Locale.ROOT);
        String remainder = commandRemainder(text, lower,
                "поставь таймер", "установи таймер", "запусти таймер", "таймер");
        if (remainder != null) {
            return timer(remainder);
        }
        remainder = commandRemainder(text, lower,
                "поставь будильник", "установи будильник", "заведи будильник", "будильник");
        if (remainder != null) {
            return alarm(remainder);
        }
        return null;
    }

    static Command parseRemote(String action, String query) {
        if ("timer".equals(action)) {
            return timer(normalizeSpokenNumbers(stripLeadingPreposition(query)));
        }
        if ("alarm".equals(action)) {
            return alarm(normalizeSpokenNumbers(stripLeadingPreposition(query)));
        }
        return null;
    }

    private static String commandRemainder(String original, String lower, String... prefixes) {
        for (String prefix : prefixes) {
            if (lower.equals(prefix) || lower.startsWith(prefix + " ")) {
                return stripLeadingPreposition(original.substring(prefix.length()).trim());
            }
        }
        return null;
    }

    private static String stripLeadingPreposition(String text) {
        return text.replaceFirst("(?iu)^(?:на|через|в)\\s+", "").trim();
    }

    private static Command timer(String input) {
        Matcher digital = TIMER_DIGITAL.matcher(input);
        if (digital.find()) {
            long minutes = Long.parseLong(digital.group(1));
            long seconds = Long.parseLong(digital.group(2));
            return checkedTimer(minutes * 60 + seconds);
        }
        Matcher matcher = DURATION.matcher(input);
        long seconds = 0;
        boolean found = false;
        while (matcher.find()) {
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException exception) {
                return new Command(Type.TIMER, 0, null,
                        "Укажи длительность таймера числами и единицами времени.");
            }
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            long multiplier = unit.startsWith("ч") ? 3600L
                    : unit.startsWith("мин") ? 60L : 1L;
            if (amount > (7L * 24 * 60 * 60 - seconds) / multiplier) {
                return new Command(Type.TIMER, 0, null, "Таймер можно поставить максимум на 7 дней.");
            }
            seconds += amount * multiplier;
            found = true;
        }
        if (!found || seconds < 1) {
            return new Command(Type.TIMER, 0, null,
                    "Назови длительность, например: «поставь таймер на 5 минут».");
        }
        return checkedTimer(seconds);
    }

    private static Command checkedTimer(long seconds) {
        if (seconds < 1 || seconds > 7L * 24 * 60 * 60) {
            return new Command(Type.TIMER, 0, null,
                    "Таймер можно поставить на срок от 1 секунды до 7 дней.");
        }
        return new Command(Type.TIMER, seconds, null, null);
    }

    private static Command alarm(String input) {
        Matcher matcher = CLOCK_TIME.matcher(input);
        boolean found = matcher.find();
        if (!found) {
            matcher = SPOKEN_TIME.matcher(input);
            found = matcher.find();
        }
        if (!found) {
            matcher = SPACED_TIME.matcher(input);
            found = matcher.find();
        }
        if (!found) {
            matcher = HOUR_ONLY.matcher(input);
            found = matcher.find();
        }
        if (!found) {
            return new Command(Type.ALARM, 0, null,
                    "Назови время будильника, например: «поставь будильник на 7:30».");
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.groupCount() < 2 || matcher.group(2) == null
                ? 0 : Integer.parseInt(matcher.group(2));
        return new Command(Type.ALARM, 0, LocalTime.of(hour, minute), null);
    }

    enum Type {
        TIMER,
        ALARM
    }

    record Command(Type type, long seconds, LocalTime alarmTime, String error) {
    }
}
