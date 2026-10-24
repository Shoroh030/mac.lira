package com.example.myjarvis;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AppNameMatcher {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("клуб бери", "clubberry"),
            Map.entry("клуббери", "clubberry"),
            Map.entry("клаб бери", "clubberry"),
            Map.entry("club berry", "clubberry"),
            Map.entry("фейсбук", "facebook"),
            Map.entry("фэйсбук", "facebook"),
            Map.entry("фейс бук", "facebook"),
            Map.entry("фэйс бук", "facebook"),
            Map.entry("фейсбуук", "facebook"),
            Map.entry("фэйсбуук", "facebook"),
            Map.entry("инстаграм", "instagram"),
            Map.entry("инстаграмм", "instagram"),
            Map.entry("инста", "instagram"),
            Map.entry("тик ток", "tiktok"),
            Map.entry("тикток", "tiktok"),
            Map.entry("ютуб", "youtube"),
            Map.entry("ютюб", "youtube"),
            Map.entry("ю туб", "youtube"),
            Map.entry("телеграм", "telegram"),
            Map.entry("телеграмм", "telegram"),
            Map.entry("телега", "telegram"),
            Map.entry("ватсап", "whatsapp"),
            Map.entry("вотсап", "whatsapp"),
            Map.entry("вацап", "whatsapp"),
            Map.entry("вк", "vk"),
            Map.entry("вконтакте", "vk"),
            Map.entry("гугл хром", "chrome"),
            Map.entry("хром", "chrome"),
            Map.entry("яндекс браузер", "yandex browser"),
            Map.entry("яндекс музыка", "yandex music"),
            Map.entry("яндекс музыку", "yandex music"),
            Map.entry("яндекс музыки", "yandex music"),
            Map.entry("музыка яндекс", "yandex music"),
            Map.entry("яндекс карты", "yandex maps"),
            Map.entry("яндекс навигатор", "yandex navigator"));

    private AppNameMatcher() {
    }

    static ResolveInfo findLauncher(PackageManager packageManager, String requestedName) {
        Set<String> queries = queryVariants(requestedName);
        if (queries.isEmpty()) {
            return null;
        }

        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = packageManager.queryIntentActivities(launcher, 0);
        ResolveInfo bestMatch = null;
        int bestScore = 0;
        for (ResolveInfo app : apps) {
            String label = canonicalize(app.loadLabel(packageManager).toString());
            String packageName = canonicalize(app.activityInfo.packageName.replace('.', ' '));
            int score = score(label, packageName, queries);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = app;
            }
        }
        return bestScore >= 60 ? bestMatch : null;
    }

    private static Set<String> queryVariants(String value) {
        Set<String> variants = new LinkedHashSet<>();
        String normalized = normalize(value);
        if (!normalized.isEmpty()) {
            addVariant(variants, normalized);
            addVariant(variants, transliterate(normalized));
            String alias = ALIASES.get(normalized);
            if (alias != null) {
                addVariant(variants, alias);
            }
        }
        return variants;
    }

    private static void addVariant(Set<String> variants, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = normalize(value);
        variants.add(compact(normalized));
        String alias = ALIASES.get(normalized);
        if (alias != null) {
            variants.add(compact(alias));
        }
    }

    private static int score(String label, String packageName, Set<String> queries) {
        String labelKey = compact(label);
        String packageKey = compact(packageName);
        int best = 0;
        for (String query : queries) {
            if (query.length() < 2) {
                continue;
            }
            if (labelKey.equals(query) || packageKey.equals(query)) {
                best = Math.max(best, 100);
            } else if (labelKey.startsWith(query) || packageKey.startsWith(query)) {
                best = Math.max(best, 80);
            } else if (query.length() >= 4
                    && (labelKey.contains(query) || packageKey.contains(query))) {
                best = Math.max(best, 65);
            } else if (query.length() >= 5) {
                int distance = Math.min(editDistance(labelKey, query),
                        editDistance(packageKey, query));
                if (distance <= Math.max(1, query.length() / 5)) {
                    best = Math.max(best, 60);
                }
            }
        }
        return best;
    }

    private static String canonicalize(String value) {
        String normalized = normalize(value);
        return ALIASES.getOrDefault(normalized, normalized);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT).trim(),
                        Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}.]+", " ")
                .trim();
    }

    private static String compact(String value) {
        return value.replace(" ", "").replace(".", "");
    }

    private static int editDistance(String first, String second) {
        if (Math.abs(first.length() - second.length()) > Math.max(1, second.length() / 5)) {
            return Integer.MAX_VALUE;
        }
        int[] previous = new int[second.length() + 1];
        for (int index = 0; index <= second.length(); index++) {
            previous[index] = index;
        }
        for (int row = 1; row <= first.length(); row++) {
            int[] current = new int[second.length() + 1];
            current[0] = row;
            for (int column = 1; column <= second.length(); column++) {
                int substitution = previous[column - 1]
                        + (first.charAt(row - 1) == second.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(substitution,
                        Math.min(previous[column] + 1, current[column - 1] + 1));
            }
            previous = current;
        }
        return previous[second.length()];
    }

    private static String transliterate(String value) {
        StringBuilder result = new StringBuilder(value.length() * 2);
        for (int index = 0; index < value.length(); index++) {
            char letter = value.charAt(index);
            switch (letter) {
                case 'а' -> result.append('a');
                case 'б' -> result.append('b');
                case 'в' -> result.append('v');
                case 'г' -> result.append('g');
                case 'д' -> result.append('d');
                case 'е', 'э' -> result.append('e');
                case 'ё' -> result.append("yo");
                case 'ж' -> result.append("zh");
                case 'з' -> result.append('z');
                case 'и' -> result.append('i');
                case 'й' -> result.append('y');
                case 'к' -> result.append('k');
                case 'л' -> result.append('l');
                case 'м' -> result.append('m');
                case 'н' -> result.append('n');
                case 'о' -> result.append('o');
                case 'п' -> result.append('p');
                case 'р' -> result.append('r');
                case 'с' -> result.append('s');
                case 'т' -> result.append('t');
                case 'у' -> result.append('u');
                case 'ф' -> result.append('f');
                case 'х' -> result.append("kh");
                case 'ц' -> result.append("ts");
                case 'ч' -> result.append("ch");
                case 'ш' -> result.append("sh");
                case 'щ' -> result.append("shch");
                case 'ы' -> result.append('y');
                case 'ю' -> result.append("yu");
                case 'я' -> result.append("ya");
                case 'ъ', 'ь' -> { }
                default -> result.append(letter);
            }
        }
        return result.toString();
    }
}
