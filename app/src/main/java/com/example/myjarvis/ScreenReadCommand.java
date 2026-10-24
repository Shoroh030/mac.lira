package com.example.myjarvis;

import java.util.Locale;

final class ScreenReadCommand {
    private ScreenReadCommand() {
    }

    static boolean matches(String command) {
        String text = command == null ? "" : command.toLowerCase(Locale.ROOT)
                .replaceAll("[,!?;:]+", " ").replace("сдесь", "здесь")
                .replace("написанно", "написано").replaceAll("\\s+", " ").trim();
        return text.contains("прочитай экран")
                || text.contains("прочитай текст на экране")
                || text.contains("прочитай что здесь написано")
                || text.contains("прочитай мне что здесь написано")
                || text.contains("прочитай мне то что здесь написано")
                || text.contains("что написано на экране")
                || text.contains("прочитай содержимое экрана")
                || text.equals("read screen")
                || text.contains("read the screen")
                || text.contains("read what's on screen")
                || text.contains("read what is on screen");
    }
}
