package com.example.lira.desktop;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

final class DesktopClockScheduler {
    private static final Logger LOGGER =
            Logger.getLogger(DesktopClockScheduler.class.getName());
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "lira-clock-scheduler");
                thread.setDaemon(true);
                return thread;
            });

    private DesktopClockScheduler() {
    }

    static String schedule(ClockCommandParser.Command command) {
        if (command.error() != null) {
            return command.error();
        }
        if (command.type() == ClockCommandParser.Type.TIMER) {
            SCHEDULER.schedule(() -> showAlert("Таймер L.I.R.A.",
                    "Время вышло."), command.seconds(), TimeUnit.SECONDS);
            return "Поставила таймер на " + formatDuration(command.seconds())
                    + ". L.I.R.A. должна оставаться запущенной на ПК.";
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime alarm = now.toLocalDate().atTime(command.alarmTime());
        if (!alarm.isAfter(now)) {
            alarm = alarm.plusDays(1);
        }
        long delay = Duration.between(now, alarm).getSeconds();
        SCHEDULER.schedule(() -> showAlert("Будильник L.I.R.A.", "Время будильника."),
                delay, TimeUnit.SECONDS);
        return "Поставила будильник на " + command.alarmTime()
                + ". Он сработает, если L.I.R.A. будет запущена.";
    }

    private static String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainder = seconds % 60;
        StringBuilder text = new StringBuilder();
        if (hours > 0) text.append(hours).append(" ч ");
        if (minutes > 0) text.append(minutes).append(" мин ");
        if (remainder > 0) text.append(remainder).append(" сек");
        return text.toString().trim();
    }

    private static void showAlert(String title, String message) {
        try {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
                alert.setTitle(title);
                alert.setHeaderText(null);
                alert.show();
            });
        } catch (IllegalStateException exception) {
            LOGGER.log(Level.FINE, "JavaFX closed before the scheduled alert.", exception);
        }
    }
}
