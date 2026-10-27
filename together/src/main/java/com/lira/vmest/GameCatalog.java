package com.lira.vmest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class GameCatalog {
    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry("tic_tac_toe", "Крестики-нолики",
                    "Сетевая партия с партнёром на другом устройстве"),
            new Entry("sea_battle", "Морской бой",
                    "Каждый расставляет свой флот на своём телефоне"),
            new Entry("durak", "Дурак",
                    "Карточная партия с партнёром на его телефоне"),
            new Entry("checkers", "Шашки",
                    "Классическая игра для двоих · каждый ход синхронизируется"),
            new Entry("connect_four", "Четыре в ряд",
                    "Бросайте фишки в колонку и соберите четыре подряд"),
            new Entry("dots_boxes", "Точки и квадратики",
                    "Соединяйте точки и замыкайте квадраты")
    ));

    private GameCatalog() { }

    static List<Entry> entries() {
        return ENTRIES;
    }

    static final class Entry {
        final String id;
        final String title;
        final String description;

        Entry(String id, String title, String description) {
            this.id = id;
            this.title = title;
            this.description = description;
        }
    }
}
