package com.example.lira.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DesktopMediaCommands {
    private static final Map<String, Integer> WINDOWS_MEDIA_KEYS = Map.of(
            "play-pause", 0xB3,
            "next", 0xB0,
            "previous", 0xB1);

    private DesktopMediaCommands() {
    }

    static String normalizeCommand(String phrase) {
        String command = phrase.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[!?.,]+$", "");
        if (matches(command, "следующий трек", "следующая песня", "переключи трек",
                "переключи песню", "next", "next track")) {
            return "next";
        }
        if (matches(command, "предыдущий трек", "предыдущая песня", "прошлый трек",
                "previous", "previous track")) {
            return "previous";
        }
        if (matches(command, "поставь на паузу", "поставь музыку на паузу",
                "пауза", "останови музыку",
                "приостанови музыку", "pause", "pause music")) {
            return "pause";
        }
        if (matches(command, "продолжи музыку", "включи музыку", "возобнови музыку",
                "играй", "play", "resume")) {
            return "play";
        }
        if (matches(command, "переключи музыку", "воспроизведение", "плей пауза",
                "play pause", "play-pause", "play or pause")) {
            return "play-pause";
        }
        if (matches(command, "поставь лайк", "лайкни", "мне нравится",
                "лайкнуть", "добавь в избранное", "like")) {
            return "like";
        }
        return null;
    }

    static RemoteCommandHandler.Result execute(String phrase)
            throws IOException, InterruptedException {
        String command = normalizeCommand(phrase);
        if (command == null) {
            return new RemoteCommandHandler.Result(false,
                    "Не распознала команду управления воспроизведением.");
        }
        if (command.equals("like")) {
            return new RemoteCommandHandler.Result(false,
                    "У музыкальных приложений нет общей команды для лайка. "
                            + "Открой экран приложения и поставь отметку там.");
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            sendWindowsMediaKey(command);
        } else if (os.contains("mac")) {
            sendMacMediaKey(command);
        } else {
            sendLinuxMediaCommand(command);
        }
        return new RemoteCommandHandler.Result(true, switch (command) {
            case "next" -> "Переключаю на следующий трек на ПК.";
            case "previous" -> "Переключаю на предыдущий трек на ПК.";
            case "pause" -> "Ставлю воспроизведение на паузу на ПК.";
            case "play" -> "Продолжаю воспроизведение на ПК.";
            default -> "Переключаю воспроизведение на ПК.";
        });
    }

    private static void sendWindowsMediaKey(String command)
            throws IOException, InterruptedException {
        int key = switch (command) {
            case "next" -> 0xB0;
            case "previous" -> 0xB1;
            case "pause" -> 0xB3;
            case "play" -> 0xB3;
            default -> WINDOWS_MEDIA_KEYS.get(command);
        };
        String script = "Add-Type -TypeDefinition 'using System.Runtime.InteropServices;"
                + "public static class LiraMediaKey {"
                + "[DllImport(\"user32.dll\")] public static extern void keybd_event("
                + "byte key, byte scan, uint flags, System.UIntPtr extra); }';"
                + "[LiraMediaKey]::keybd_event(" + key
                + ",0,0,[System.UIntPtr]::Zero);"
                + "[LiraMediaKey]::keybd_event(" + key
                + ",0,2,[System.UIntPtr]::Zero)";
        run(List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-WindowStyle", "Hidden", "-Command", script));
    }

    private static void sendMacMediaKey(String command)
            throws IOException, InterruptedException {
        String script = switch (command) {
            case "next" -> "tell application \"System Events\" to key code 17";
            case "previous" -> "tell application \"System Events\" to key code 18";
            case "pause" -> "tell application \"System Events\" to key code 16";
            case "play" -> "tell application \"System Events\" to key code 16";
            default -> "tell application \"System Events\" to key code 16";
        };
        run(List.of("osascript", "-e", script));
    }

    private static void sendLinuxMediaCommand(String command)
            throws IOException, InterruptedException {
        String action = switch (command) {
            case "next" -> "next";
            case "previous" -> "previous";
            case "pause" -> "pause";
            case "play" -> "play";
            default -> "play-pause";
        };
        run(List.of("playerctl", action));
    }

    private static void run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String detail = new String(output, StandardCharsets.UTF_8).trim();
            throw new IOException(detail.isEmpty()
                    ? "Системный медиаплеер не ответил." : detail);
        }
    }

    private static boolean matches(String command, String... aliases) {
        for (String alias : aliases) {
            if (command.equals(alias) || command.startsWith(alias + " ")) {
                return true;
            }
        }
        return false;
    }
}
