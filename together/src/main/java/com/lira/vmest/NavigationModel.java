package com.lira.vmest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class NavigationModel {
    static final String MORE_TAB = "Ещё";
    static final String SETTINGS_TAB = "Настройки";
    private static final int MAX_PRIMARY_TABS = 3;

    private NavigationModel() { }

    static List<String> defaultOrder() {
        return Arrays.asList(
                "Главная", "Чат", "Дела", SETTINGS_TAB, MORE_TAB, "Календарь", "Хотим", "Игры",
                "Просмотр", "Пригласить", "Подарочный радар", "Истории", "Капсула",
                "Карты", "Голосование", "Файлы");
    }

    static Set<String> defaultHidden() {
        return new HashSet<>(Arrays.asList(
                "Календарь", "Хотим", "Игры", "Просмотр", "Пригласить", "Подарочный радар",
                "Истории", "Капсула", "Карты", "Голосование", "Файлы"));
    }

    static List<String> bottomTabs(List<String> order, Set<String> hidden) {
        ArrayList<String> result = new ArrayList<>();
        if (order != null) {
            for (String item : order) {
                if (item == null || MORE_TAB.equals(item) || SETTINGS_TAB.equals(item)
                        || (hidden != null && hidden.contains(item))) {
                    continue;
                }
                if (!result.contains(item)) result.add(item);
                if (result.size() == MAX_PRIMARY_TABS) break;
            }
        }
        result.add(SETTINGS_TAB);
        result.add(MORE_TAB);
        return result;
    }
}
