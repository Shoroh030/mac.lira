package com.example.myjarvis;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LiraBackgroundService extends Service {

    public static final String ACTION_ENABLE = "com.example.myjarvis.action.ENABLE_VOICE";
    public static final String ACTION_DISABLE = "com.example.myjarvis.action.DISABLE_VOICE";
    public static final String ACTION_WIDGET_LISTEN =
            "com.example.myjarvis.action.WIDGET_LISTEN";
    public static final String ACTION_COMMAND = "com.example.myjarvis.action.VOICE_COMMAND";
    public static final String EXTRA_VOICE_COMMAND = "voice_command";
    public static final String EXTRA_START_VOICE = "start_voice_recognition";

    private static final String TAG = "LiraBackgroundService";
    private static final String CHANNEL_ID = "LiraVoiceServiceChannel";
    private static final String RESULT_CHANNEL_ID = "LiraVoiceResults";
    private static final int NOTIFICATION_ID = 1337;
    private static final int COMMAND_NOTIFICATION_ID = 1338;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final String PREFERENCES = "LiraPrefs";

    private volatile boolean listening;
    private volatile boolean destroyed;
    private AudioRecord audioRecord;
    private ExecutorService audioExecutor;
    private ExecutorService commandExecutor;
    private Model model;
    private long wakeWordExpiry;
    private boolean widgetOneShot;
    private boolean widgetCommandPending;
    private OfflineVoiceRecognizer widgetRecognizer;

    @Override
    public void onCreate() {
        super.onCreate();
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DISABLE.equals(intent.getAction())) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                    .putBoolean("background_voice_enabled", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean widgetRequest = intent != null
                && ACTION_WIDGET_LISTEN.equals(intent.getAction());
        if (widgetRequest && listening) {
            notifyCommand("Фоновый микрофон уже слушает. Скажи «Лира» и команду.",
                    null);
            return START_STICKY;
        }
        widgetOneShot = widgetRequest;
        if (widgetOneShot && !getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean("floating_widget_enabled", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!widgetOneShot && !getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getBoolean("background_voice_enabled", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundServiceNotification(widgetOneShot);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            reportFailure("Нет разрешения на микрофон. Открой L.I.R.A. и разреши доступ "
                    + "к микрофону для плавающей кнопки.");
            if (!widgetOneShot) {
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                        .putBoolean("background_voice_enabled", false).apply();
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        if (widgetOneShot) {
            startWidgetRecognition();
            return START_NOT_STICKY;
        }
        if (!listening) {
            initializeOfflineModel();
        }
        return START_STICKY;
    }

    private void startWidgetRecognition() {
        widgetRecognizer = new OfflineVoiceRecognizer(this,
                new OfflineVoiceRecognizer.Listener() {
                    @Override
                    public void onResult(String text) {
                        widgetRecognizer = null;
                        executeOrNotify(text);
                        if (!widgetCommandPending) {
                            stopSelf();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        widgetRecognizer = null;
                        notifyCommand(message, null);
                        stopSelf();
                    }
                });
        widgetRecognizer.start();
    }

    private void initializeOfflineModel() {
        listening = true;
        try {
            StorageService.unpack(this, "model-ru", "model-ru",
                    loadedModel -> {
                        if (destroyed || !listening) {
                            loadedModel.close();
                            return;
                        }
                        model = loadedModel;
                        startListening();
                    },
                    exception -> {
                        Log.e(TAG, "Failed to load the offline speech model", exception);
                        reportFailure("Не удалось загрузить офлайн-модель речи. Перезапусти режим.");
                        stopSelf();
                    });
        } catch (RuntimeException exception) {
            Log.e(TAG, "Failed to initialize the offline speech model", exception);
            reportFailure("Не удалось запустить офлайн-распознавание.");
            stopSelf();
        }
    }

    private void startForegroundServiceNotification(boolean widgetMode) {
        PendingIntent contentIntent = null;
        if (!widgetMode) {
            Intent openIntent = new Intent(this, MainActivity.class);
            contentIntent = PendingIntent.getActivity(this, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        Intent stopIntent = new Intent(this, LiraBackgroundService.class);
        stopIntent.setAction(ACTION_DISABLE);
        PendingIntent stopPendingIntent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? PendingIntent.getForegroundService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("L.I.R.A. слушает «Лира»")
                .setContentText(widgetMode ? "Слушаю команду через плавающую кнопку."
                        : "Офлайн-микрофон активен. Коснись, чтобы выключить.")
                .setSmallIcon(R.drawable.ic_lira_logo)
                .setContentIntent(contentIntent)
                .setOngoing(true);
        if (!widgetMode) {
            builder.addAction(android.R.drawable.ic_media_pause, "Выключить", stopPendingIntent);
        }
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                "Фоновое распознавание L.I.R.A.", NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(RESULT_CHANNEL_ID,
                "Голосовые команды L.I.R.A.", NotificationManager.IMPORTANCE_DEFAULT));
    }

    private void startListening() {
        if (audioExecutor != null || destroyed || !listening || model == null) {
            return;
        }
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize <= 0) {
            reportFailure("Устройство не поддерживает запись с микрофона.");
            stopSelf();
            return;
        }
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, Math.max(bufferSize, 8192));
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                reportFailure("Не удалось инициализировать микрофон.");
                stopSelf();
                return;
            }
            audioRecord.startRecording();
            audioExecutor = Executors.newSingleThreadExecutor();
            audioExecutor.execute(this::recognizeCommands);
        } catch (SecurityException | IllegalStateException exception) {
            Log.e(TAG, "Could not start background audio recording", exception);
            reportFailure("Не удалось включить микрофон. Проверь разрешения и настройки батареи.");
            stopSelf();
        }
    }

    private void recognizeCommands() {
        AudioRecord recorder = audioRecord;
        Model voiceModel = model;
        if (recorder == null || voiceModel == null) {
            return;
        }
        try (Recognizer recognizer = new Recognizer(voiceModel, SAMPLE_RATE)) {
            recognizer.setMaxAlternatives(4);
            byte[] buffer = new byte[4096];
            while (listening && !destroyed) {
                int bytesRead = recorder.read(buffer, 0, buffer.length);
                if (bytesRead < 0) {
                    if (listening && !destroyed) {
                        throw new IOException("AudioRecord returned " + bytesRead);
                    }
                    break;
                }
                if (bytesRead > 0 && recognizer.acceptWaveForm(buffer, bytesRead)) {
                    processRecognizedText(SpeechTextSelector.select(
                            new JSONObject(recognizer.getResult())));
                }
            }
        } catch (JSONException | IOException | RuntimeException exception) {
            if (listening && !destroyed) {
                Log.e(TAG, "Background voice recognition failed", exception);
                reportFailure("Фоновое распознавание остановилось: проверь микрофон.");
                stopSelf();
            }
        } finally {
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    recorder.stop();
                } catch (IllegalStateException exception) {
                    Log.w(TAG, "The background audio recorder was already stopped.", exception);
                }
            }
            recorder.release();
            if (audioRecord == recorder) {
                audioRecord = null;
            }
            voiceModel.close();
            if (model == voiceModel) {
                model = null;
            }
            audioExecutor = null;
        }
    }

    private void processRecognizedText(String recognized) {
        String text = recognized.trim();
        if (text.isEmpty()) {
            return;
        }
        java.util.regex.Matcher wakeMatcher = java.util.regex.Pattern.compile(
                "^(?:(?:эй|окей|ок|слушай)\\s+)*(?:лира|лиро|лера|мира|ира|lira)"
                        + "(?=\\s|$|[,.:;!?—-])(.*)$",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE)
                .matcher(text);
        if (wakeMatcher.matches()) {
            text = wakeMatcher.group(1).trim()
                    .replaceAll("^[\\s,.:;!?—-]+", "")
                    .replaceAll("(?iu)^(?:(?:лира|лиро|лера|мира|ира|lira)"
                            + "[\\s,.:;!?—-]*)+", "");
            wakeWordExpiry = System.currentTimeMillis() + 12000L;
            if (text.isEmpty()) {
                return;
            }
        } else if (System.currentTimeMillis() > wakeWordExpiry) {
            return;
        } else {
            wakeWordExpiry = 0;
        }
        wakeWordExpiry = 0;
        executeOrNotify(text);
    }

    private void executeOrNotify(String command) {
        String lower = command.toLowerCase(new Locale("ru"));
        PcCommandClient.Command pcCommand = PcCommandClient.parse(command);
        if (pcCommand != null) {
            if (pcCommand.query.isEmpty()) {
                notifyCommand(pcCommand.action.equals("search")
                        ? "Скажи, что найти в браузере на ПК."
                        : pcCommand.action.equals("timer")
                        ? "Назови длительность таймера на ПК."
                        : pcCommand.action.equals("alarm")
                        ? "Назови время будильника на ПК."
                        : "Назови программу, документ или папку на ПК.", null);
                return;
            }
            if (commandExecutor == null || commandExecutor.isShutdown()) {
                commandExecutor = Executors.newSingleThreadExecutor();
            }
            widgetCommandPending = widgetOneShot;
            commandExecutor.execute(() -> {
                String result;
                try {
                    result = PcCommandClient.execute(this, pcCommand);
                } catch (IOException exception) {
                    result = "Не удалось выполнить команду на ПК: " + exception.getMessage();
                    Log.w(TAG, "Failed to send a voice command to the paired PC", exception);
                }
                if (!destroyed) {
                    notifyCommand(result, null);
                    if (widgetOneShot) {
                        stopSelf();
                    }
                }
            });
            return;
        }
        if (ScreenReadCommand.matches(command)) {
            boolean started = LiraScreenReaderService.speakLastExternalScreen();
            notifyCommand(started
                    ? "Читаю текст последнего открытого экрана."
                    : "Не удалось прочитать экран. Включи службу чтения экрана в настройках "
                            + "L.I.R.A. и открой нужный экран перед командой.", null);
            if (widgetOneShot) {
                stopSelf();
            }
            return;
        }
        AndroidClockCommands.Result clockCommand = AndroidClockCommands.parse(command);
        if (clockCommand != null) {
            if (clockCommand.intent == null) {
                notifyCommand(clockCommand.message, null);
                return;
            }
            clockCommand.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchFromBackgroundOrNotify(clockCommand.intent,
                    "Android не разрешил открыть приложение «Часы». "
                            + "Нажми уведомление, чтобы продолжить.");
            return;
        }
        TelegramDraftCommand.Draft telegramDraft = TelegramDraftCommand.parse(command);
        if (telegramDraft != null) {
            openTelegramDraftFromBackground(telegramDraft);
            return;
        }
        String mediaResponse = MediaCommands.handle(this, command);
        if (mediaResponse != null) {
            notifyCommand(mediaResponse, null);
            if (widgetOneShot) {
                stopSelf();
            }
            return;
        }
        String callTarget = PhoneCallCommands.targetFrom(command);
        if (callTarget != null) {
            dialFromBackground(callTarget);
            return;
        }
        List<FinanceVoiceParser.Entry> entries = FinanceVoiceParser.parse(command);
        if (!entries.isEmpty()) {
            OfflineDataStore store = new OfflineDataStore(this);
            try {
                String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                for (FinanceVoiceParser.Entry entry : entries) {
                    store.addTransaction(entry.title, entry.amountCents, entry.income,
                            date, entry.category);
                }
            } catch (RuntimeException exception) {
                Log.e(TAG, "Failed to save voice-recorded finance entries", exception);
                notifyCommand("Не удалось сохранить финансовую операцию.", null);
                return;
            } finally {
                store.close();
            }
            notifyCommand("Записала " + entries.size() + " финансовых операций.", null);
            return;
        }

        if (startsWithCommand(lower, "запиши заметку", "добавь заметку", "заметка", "запомни")) {
            String note = removeCommandPrefix(command,
                    "запиши заметку", "добавь заметку", "заметка", "запомни");
            if (note.isEmpty()) {
                notifyCommand("Не услышала текст заметки.", command);
                return;
            }
            OfflineDataStore store = new OfflineDataStore(this);
            try {
                String created = new SimpleDateFormat("yyyy-MM-dd HH:mm",
                        Locale.US).format(new Date());
                String title = "Заметка " + new SimpleDateFormat("d MMM, HH:mm",
                        new Locale("ru")).format(new Date());
                store.addVoiceNote(title, note, created);
            } catch (RuntimeException exception) {
                Log.e(TAG, "Failed to save a background voice note", exception);
                notifyCommand("Не удалось сохранить заметку.", null);
                return;
            } finally {
                store.close();
            }
            notifyCommand("Голосовая заметка сохранена. Её можно открыть во вкладке «Цели».",
                    null);
            return;
        }

        if (startsWithCommand(lower, "найди", "найти", "поищи", "поиск", "search",
                "search for")) {
            String query = removeCommandPrefix(command, "найди", "найти", "поищи",
                    "поиск", "search for", "search");
            if (query.isEmpty()) {
                notifyCommand("Скажи, что найти в интернете.", null);
                return;
            }
            Intent searchIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
            searchIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launchFromBackgroundOrNotify(searchIntent,
                    "Не удалось открыть поиск. Нажми уведомление, чтобы открыть браузер.");
            return;
        }

        if (startsWithCommand(lower, "открой", "открыть", "запусти", "запустить",
                "откройте", "запустите", "open", "launch", "start", "run")) {
            String appName = removeCommandPrefix(command,
                    "открой", "открыть", "запусти", "запустить", "откройте", "запустите",
                    "open", "launch", "start", "run")
                    .replaceFirst("(?iu)^(?:(?:мне|пожалуйста|приложение|app|the)\\s+)+", "");
            Intent launchIntent = findAppLaunchIntent(appName);
            if (launchIntent == null) {
                notifyCommand(appName.isEmpty() ? "Назови приложение, которое нужно открыть."
                        : "Приложение «" + appName + "» не найдено.", null);
                return;
            }
            launchFromBackgroundOrNotify(launchIntent,
                    "Android не разрешил открыть приложение «" + appName
                            + "» из фона. Нажми уведомление, чтобы открыть его.");
            return;
        }
        notifyCommand("Пока не умею выполнить эту команду в фоне: " + command, null);
    }

    private void openTelegramDraftFromBackground(TelegramDraftCommand.Draft draft) {
        for (String packageName : new String[]{
                "org.telegram.messenger", "org.thunderdog.challegram"}) {
            try {
                getPackageManager().getPackageInfo(packageName, 0);
                Intent intent = TelegramDraftCommand.openTelegramDraft(draft, packageName);
                launchFromBackgroundOrNotify(intent,
                        "Нажми уведомление, чтобы открыть Telegram с черновиком сообщения.");
                return;
            } catch (PackageManager.NameNotFoundException exception) {
                continue;
            } catch (ActivityNotFoundException exception) {
                notifyCommand("Не удалось открыть Telegram с черновиком.", null);
                return;
            }
        }
        notifyCommand("Telegram не найден на устройстве.", null);
    }

    private void dialFromBackground(String target) {
        if (target.isEmpty()) {
            notifyCommand("Скажи имя контакта или номер телефона.", null);
            return;
        }
        String number = target;
        if (!PhoneCallCommands.isPhoneNumber(target)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifyCommand("Чтобы искать контакт, открой L.I.R.A. и разреши доступ "
                        + "к контактам.", null);
                return;
            }
            try {
                List<PhoneCallCommands.Contact> contacts =
                        PhoneCallCommands.findContacts(this, target);
                if (contacts.size() != 1) {
                    notifyCommand(contacts.isEmpty()
                                    ? "Контакт не найден. Скажи номер или проверь имя."
                                    : "Нашла несколько контактов. Открой L.I.R.A. и выбери нужный.",
                            null);
                    return;
                }
                number = contacts.get(0).number;
            } catch (SecurityException exception) {
                notifyCommand("Нет разрешения на чтение контактов. Скажи номер телефона.", null);
                return;
            }
        }
        Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
        launchFromBackgroundOrNotify(dial,
                "Нажми уведомление, чтобы открыть набор номера и подтвердить звонок.");
    }

    private boolean startsWithCommand(String text, String... commands) {
        for (String command : commands) {
            if (text.equals(command) || text.startsWith(command + " ")) {
                return true;
            }
        }
        return false;
    }

    private String removeCommandPrefix(String text, String... commands) {
        for (String command : commands) {
            if (text.regionMatches(true, 0, command, 0, command.length())
                    && (text.length() == command.length()
                    || Character.isWhitespace(text.charAt(command.length())))) {
                return text.substring(command.length()).trim();
            }
        }
        return text.trim();
    }

    @Nullable
    private Intent findAppLaunchIntent(String name) {
        if (name.isEmpty()) {
            return null;
        }
        PackageManager packageManager = getPackageManager();
        ResolveInfo bestMatch = AppNameMatcher.findLauncher(packageManager, name);
        if (bestMatch == null) {
            return null;
        }
        Intent launchIntent = packageManager.getLaunchIntentForPackage(
                bestMatch.activityInfo.packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return launchIntent;
    }

    private void launchFromBackgroundOrNotify(Intent intent, String fallbackMessage) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException | SecurityException
                 | IllegalArgumentException exception) {
            Log.w(TAG, "Android blocked a background activity launch", exception);
            notifyCommand(fallbackMessage, null, intent);
        }
    }

    private void notifyCommand(String message, @Nullable String command) {
        notifyCommand(message, command, null);
    }

    private void notifyCommand(String message, @Nullable String command,
                               @Nullable Intent actionIntent) {
        PendingIntent pendingIntent = null;
        if (actionIntent != null) {
            pendingIntent = PendingIntent.getActivity(this,
                    command == null ? message.hashCode() : command.hashCode(), actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else if (command != null) {
            Intent openIntent = new Intent(this, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            openIntent.putExtra(EXTRA_VOICE_COMMAND, command);
            pendingIntent = PendingIntent.getActivity(this, command.hashCode(), openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
                .setContentTitle("L.I.R.A.")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_lira_logo)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent);
        }
        Notification notification = builder.build();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(COMMAND_NOTIFICATION_ID, notification);
        }
    }

    private void reportFailure(String message) {
        Log.w(TAG, message);
        notifyCommand(message, null);
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        listening = false;
        if (widgetRecognizer != null) {
            widgetRecognizer.stop();
            widgetRecognizer = null;
        }
        AudioRecord recorder = audioRecord;
        if (recorder != null) {
            try {
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop();
                }
            } catch (IllegalStateException exception) {
                Log.w(TAG, "The background audio recorder was already stopped.", exception);
            }
        }
        if (audioExecutor != null) {
            audioExecutor.shutdownNow();
            audioExecutor = null;
        } else {
            if (recorder != null) {
                recorder.release();
            }
            if (model != null) {
                model.close();
            }
            model = null;
        }
        if (commandExecutor != null) {
            commandExecutor.shutdownNow();
            commandExecutor = null;
        }
        audioRecord = null;
        if (!widgetOneShot) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                    .putBoolean("background_voice_enabled", false).apply();
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
