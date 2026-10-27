package com.example.lira.desktop;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

final class RemoteCommandHandler {
    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_VISITED_ENTRIES = 100_000;
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(20);
    private static final List<String> WINDOWS_SYSTEM_DIRECTORIES = List.of(
            "windows", "program files", "program files (x86)", "programdata",
            "$recycle.bin", "system volume information", "windowsapps");
    private static final List<String> OPENABLE_EXTENSIONS = List.of(
            ".txt", ".rtf", ".pdf", ".doc", ".docx", ".odt",
            ".xls", ".xlsx", ".ods", ".csv", ".ppt", ".pptx", ".odp",
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp",
            ".mp3", ".wav", ".m4a", ".ogg", ".mp4", ".mov", ".avi", ".mkv");

    private final ApplicationLauncher applicationLauncher;

    RemoteCommandHandler(ApplicationLauncher applicationLauncher) {
        this.applicationLauncher = applicationLauncher;
    }

    Result execute(String action, String requested) {
        String query = requested == null ? "" : requested.trim();
        if (query.isEmpty() || query.length() > MAX_QUERY_LENGTH
                || query.chars().anyMatch(Character::isISOControl)) {
            return new Result(false, "Команда пустая или слишком длинная.");
        }

        try {
            return switch (action) {
                case "launch_app" -> launchApplication(query);
                case "open" -> openApplicationOrFile(query);
                case "search" -> searchWeb(query);
                case "timer", "alarm" -> scheduleClockCommand(action, query);
                case "media" -> DesktopMediaCommands.execute(query);
                default -> new Result(false, "Неизвестный тип команды.");
            };
        } catch (IOException exception) {
            return new Result(false, "Не удалось открыть запрошенный элемент: "
                    + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Result(false, "Операция прервана.");
        }
    }

    private Result launchApplication(String name) throws IOException, InterruptedException {
        return applicationLauncher.open(name)
                ? new Result(true, "Запускаю на ПК «" + name + "».")
                : new Result(false, "Установленное приложение «" + name + "» не найдено.");
    }

    private Result openApplicationOrFile(String name) throws IOException, InterruptedException {
        if (isRecycleBin(name)) {
            return openRecycleBin();
        }
        Result systemFolder = openSystemFolder(name);
        if (systemFolder != null) {
            return systemFolder;
        }
        if (applicationLauncher.open(name)) {
            return new Result(true, "Открываю на ПК «" + name + "».");
        }
        Path match = findFile(name);
        if (match == null) {
            return new Result(false, "Не нашла приложение, документ или папку «" + name
                    + "» на ПК.");
        }
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            return new Result(false, "Открытие файлов этой системой не поддерживается.");
        }
        Desktop.getDesktop().open(match.toFile());
        return new Result(true, "Открываю на ПК: " + match.getFileName() + ".");
    }

    private Result openSystemFolder(String name) throws IOException {
        String normalized = name.toLowerCase(Locale.ROOT)
                .replaceFirst("^(?:(?:системную|system)\\s+)?(?:папку|папка|folder)\\s+", "")
                .replaceAll("[^\\p{L}\\p{N}]", "");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path directory = null;
        if (os.contains("win")) {
            String windows = System.getenv("SystemRoot");
            switch (normalized) {
                case "windows", "виндовс", "папкаwindows" ->
                        directory = windows == null ? null : Path.of(windows);
                case "system32", "систем32", "система32" ->
                        directory = windows == null ? null : Path.of(windows, "System32");
                case "drivers", "драйверы" ->
                        directory = windows == null ? null
                                : Path.of(windows, "System32", "drivers");
                case "programfiles", "файлыпрограмм", "программныефайлы" ->
                        directory = systemDirectoryFromEnvironment("ProgramFiles");
                case "programfilesx86", "файлыпрограммx86", "программныефайлыx86" ->
                        directory = systemDirectoryFromEnvironment("ProgramFiles(x86)");
                case "programdata", "общиеданныепрограмм" ->
                        directory = systemDirectoryFromEnvironment("ProgramData");
                case "appdata", "данныеприложений" ->
                        directory = systemDirectoryFromEnvironment("APPDATA");
                case "temp", "временныефайлы" ->
                        directory = systemDirectoryFromEnvironment("TEMP");
                default -> {
                    return null;
                }
            }
        } else if (os.contains("mac")) {
            directory = switch (normalized) {
                case "system", "система" -> Path.of("/System");
                case "library", "библиотека" -> Path.of("/Library");
                case "applications", "программы" -> Path.of("/Applications");
                default -> null;
            };
        } else {
            directory = switch (normalized) {
                case "etc", "настройкисистемы" -> Path.of("/etc");
                case "usr", "программысистемы" -> Path.of("/usr");
                case "var", "системныеданные" -> Path.of("/var");
                case "opt" -> Path.of("/opt");
                default -> null;
            };
        }
        if (directory == null) {
            return null;
        }
        if (!Files.isDirectory(directory)) {
            return new Result(false, "Системная папка недоступна: " + directory + ".");
        }
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            return new Result(false, "Открытие системных папок не поддерживается.");
        }
        Desktop.getDesktop().open(directory.toFile());
        return new Result(true, "Открываю системную папку: " + directory + ".");
    }

    private Path systemDirectoryFromEnvironment(String variable) {
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private Result scheduleClockCommand(String action, String query) {
        ClockCommandParser.Command command = ClockCommandParser.parseRemote(action, query);
        if (command == null) {
            return new Result(false, "Не удалось распознать команду таймера или будильника.");
        }
        if (command.error() != null) {
            return new Result(false, command.error());
        }
        return new Result(true, DesktopClockScheduler.schedule(command));
    }

    private Result openRecycleBin() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            new ProcessBuilder("explorer.exe", "shell:RecycleBinFolder").start();
            return new Result(true, "Открываю Корзину на ПК.");
        }
        if (os.contains("mac")) {
            Path trash = Path.of(System.getProperty("user.home"), ".Trash");
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                return new Result(false, "Не удалось открыть Корзину на этом Mac.");
            }
            Desktop.getDesktop().open(trash.toFile());
            return new Result(true, "Открываю Корзину на Mac.");
        }
        if (Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create("trash:///"));
            return new Result(true, "Открываю Корзину на ПК.");
        }
        return new Result(false, "Открытие Корзины не поддерживается этой системой.");
    }

    private boolean isRecycleBin(String name) {
        String normalized = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
        return normalized.equals("корзина") || normalized.equals("корзину")
                || normalized.equals("карзина") || normalized.equals("карзину")
                || normalized.equals("recyclebin") || normalized.equals("trash")
                || normalized.equals("rubbishbin");
    }

    private Result searchWeb(String query) throws IOException {
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            return new Result(false, "Системный браузер на ПК недоступен.");
        }
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        Desktop.getDesktop().browse(URI.create("https://www.google.com/search?q=" + encoded));
        return new Result(true, "Открыла поиск в браузере на ПК: " + query);
    }

    private Path findFile(String query) throws IOException {
        String normalized = query.toLowerCase(Locale.ROOT);
        List<Path> exactMatches = new ArrayList<>();
        List<Path> partialMatches = new ArrayList<>();
        SearchState desktopState = new SearchState(System.nanoTime()
                + SEARCH_TIMEOUT.toNanos(), normalized, exactMatches, partialMatches);
        for (Path desktop : desktopDirectories()) {
            scanRoot(desktop, desktopState);
            if (desktopState.limitReached) {
                break;
            }
        }
        Path desktopMatch = resolveMatches(desktopState, "Рабочем столе");
        if (desktopMatch != null) {
            return desktopMatch;
        }
        if (desktopState.limitReached) {
            throw new IOException("Поиск на Рабочем столе занял слишком много времени. "
                    + "Уточни название.");
        }

        exactMatches.clear();
        partialMatches.clear();
        SearchState driveState = new SearchState(System.nanoTime()
                + SEARCH_TIMEOUT.toNanos(), normalized, exactMatches, partialMatches);
        for (Path root : Path.of("").getFileSystem().getRootDirectories()) {
            if (driveState.limitReached) {
                break;
            }
            scanRoot(root, driveState);
        }
        Path diskMatch = resolveMatches(driveState, "дисках");
        if (diskMatch != null) {
            return diskMatch;
        }
        if (driveState.limitReached) {
            throw new IOException("Поиск по дискам занял слишком много времени. Уточни название.");
        }
        return null;
    }

    private List<Path> desktopDirectories() {
        Path home = Path.of(System.getProperty("user.home"));
        List<Path> candidates = new ArrayList<>();
        candidates.add(home.resolve("Desktop"));
        for (String key : List.of("OneDrive", "OneDriveConsumer", "OneDriveCommercial")) {
            String environmentPath = System.getenv(key);
            if (environmentPath != null && !environmentPath.isBlank()) {
                candidates.add(Path.of(environmentPath).resolve("Desktop"));
            }
        }
        return candidates.stream().distinct().filter(Files::isDirectory).toList();
    }

    private void scanRoot(Path root, SearchState state) throws IOException {
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 20, state);
        } catch (AmbiguousMatch exception) {
            throw exception;
        } catch (SearchLimitReached ignored) {
            state.limitReached = true;
        } catch (IOException | SecurityException ignored) {
            // Unreadable folders and removable drives must not stop the search elsewhere.
        }
    }

    private Path resolveMatches(SearchState state, String location) throws IOException {
        List<Path> matches = state.exactMatches.isEmpty()
                ? state.partialMatches : state.exactMatches;
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            throw new IOException("Найдено несколько совпадений на " + location
                    + ". Уточни название или расширение.");
        }
        return null;
    }

    static boolean canOpen(Path path) {
        if (Files.isDirectory(path)) {
            return true;
        }
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return OPENABLE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static boolean isWindowsSystemDirectory(Path directory) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win") || directory.getParent() == null
                || directory.getParent().getParent() != null) {
            return false;
        }
        String name = directory.getFileName().toString().toLowerCase(Locale.ROOT);
        return WINDOWS_SYSTEM_DIRECTORIES.contains(name);
    }

    record Result(boolean success, String message) {
    }

    private static final class SearchState implements FileVisitor<Path> {
        private final long deadline;
        private final String query;
        private final List<Path> exactMatches;
        private final List<Path> partialMatches;
        private int visitedEntries;
        private boolean limitReached;

        private SearchState(long deadline, String query, List<Path> exactMatches,
                            List<Path> partialMatches) {
            this.deadline = deadline;
            this.query = query;
            this.exactMatches = exactMatches;
            this.partialMatches = partialMatches;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                throws IOException {
            if (attributes.isSymbolicLink() || isWindowsSystemDirectory(directory)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            inspect(directory);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                throws IOException {
            if (!attributes.isSymbolicLink()) {
                inspect(file);
            }
            return FileVisitResult.CONTINUE;
        }

        private void inspect(Path path) throws IOException {
            visitedEntries++;
            if (visitedEntries > MAX_VISITED_ENTRIES || System.nanoTime() > deadline) {
                limitReached = true;
                throw new SearchLimitReached();
            }
            Path fileName = path.getFileName();
            if (fileName == null || !canOpen(path)) {
                return;
            }
            String name = fileName.toString().toLowerCase(Locale.ROOT);
            String withoutExtension = name.replaceFirst("\\.[^.]+$", "");
            if (name.equals(query) || withoutExtension.equals(query)) {
                exactMatches.add(path);
            } else if (query.length() >= 3 && name.contains(query)
                    && partialMatches.size() < 20) {
                partialMatches.add(path);
            }
            if (exactMatches.size() > 1) {
                throw new AmbiguousMatch();
            }
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            return FileVisitResult.SKIP_SUBTREE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException exception) {
            return FileVisitResult.CONTINUE;
        }
    }

    private static final class SearchLimitReached extends IOException {
    }

    private static final class AmbiguousMatch extends IOException {
        private AmbiguousMatch() {
            super("Найдено несколько совпадений. Уточни название или расширение.");
        }
    }
}
