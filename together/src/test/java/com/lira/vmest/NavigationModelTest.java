package com.lira.vmest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class NavigationModelTest {
    @Test
    public void defaultBottomNavigationKeepsMoreAccessible() {
        List<String> tabs = NavigationModel.bottomTabs(
                NavigationModel.defaultOrder(), NavigationModel.defaultHidden());

        assertEquals(Arrays.asList("Главная", "Чат", "Дела", "Календарь", "Ещё"), tabs);
    }

    @Test
    public void moreRemainsAvailableEvenIfOldPreferencesHideIt() {
        List<String> tabs = NavigationModel.bottomTabs(
                NavigationModel.defaultOrder(),
                Collections.singleton("Ещё"));

        assertTrue(tabs.contains("Ещё"));
        assertEquals(5, tabs.size());
    }

    @Test
    public void respectsVisibleCustomRoutesAndCapsBottomNavigationAtFive() {
        List<String> tabs = NavigationModel.bottomTabs(
                Arrays.asList("Игры", "Просмотр", "Карты", "Файлы", "Календарь", "Ещё"),
                Collections.singleton("Просмотр"));

        assertEquals(Arrays.asList("Игры", "Карты", "Файлы", "Календарь", "Ещё"), tabs);
    }

    @Test
    public void fallsBackToMoreWhenEveryCustomRouteIsHidden() {
        List<String> tabs = NavigationModel.bottomTabs(
                Arrays.asList("Игры", "Просмотр"),
                new java.util.HashSet<>(Arrays.asList("Игры", "Просмотр")));

        assertEquals(Collections.singletonList("Ещё"), tabs);
    }
}
