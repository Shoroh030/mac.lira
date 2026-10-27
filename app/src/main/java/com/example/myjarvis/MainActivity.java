package com.example.myjarvis;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private ImageButton btnMenu, btnSend, btnVoice;
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private EditText etMessage;
    private TextView connectionStatus;
    private android.view.View chatScreen;
    private android.view.View moduleContainer;
    private android.view.View btnTabChat;
    private android.view.View btnTabFinance;
    private android.view.View btnTabGoals;
    private GestureDetector tabGestureDetector;
    private OfflineDataStore dataStore;
    private static final int REQUEST_CODE_CAMERA = 101;
    private static final int REQUEST_CODE_MICROPHONE = 102;
    private static final String STATE_SELECTED_TAB = "selected_tab";
    private OfflineVoiceRecognizer voiceRecognizer;
    private TextToSpeech textToSpeech;
    private boolean speechEngineInitialized;
    private boolean speechReady;
    private boolean speechFailureReported;
    private int voiceRequestId;
    private boolean isFlashlightOn = false;
    private String selectedTab = "chat";
    private ConnectivityManager.NetworkCallback networkCallback;
    private ActivityResultLauncher<String> contactsPermissionLauncher;
    private String pendingCallTarget;
    private final ExecutorService pcCommandExecutor = Executors.newSingleThreadExecutor();
    private String userName = null;

// Goal-setting assistant state
private boolean isGoalSettingSessionActive = false;
private StringBuilder goalSettingResponses = new StringBuilder();
private int goalSettingStep = 0;
private static final int GOAL_SETTING_STEPS = 5;
private String[] goalSettingQuestions = {
    "Р”РµР»РµР№ РѕС‚РєСЂС‹РІР°СЋ РѕС„Р»Р°Р№РЅ РґР»СЏ Р·Р°РїСЂРѕРіРЅРѕР· РїСЂРѕС‚РёРІР°С‚СЊ РїРѕР»Рµ? (Например: научиться играть на гитаре, пробежать марафон, выучить язык)",
    "РћРїС‚РёРјРёСЂРѕРІР°РЅРёРµ РЅР° С†РµР»РµР№: РїСЂРѕРёСЃСЊ РєР»СЋС‡РµРЅРёРµ (1-30 РґРЅ.), РёРЅС‚РµСЂС†РµР»СЊ (1-12 РјЅ.), РіРµРЅСЊ (1+ РіРµРЅСЊ)",
    "Р”РµР»РµР№ РѕС‚РєСЂС‹РІР°СЋ РІС‹РІРµ: РїСЂРѕРёСЃСЊ РєР»СЋС‡РµРЅРёРµ, РїСЂРѕРёРІРѕС‚СЊ РґР»СЏ РјРµР»Рµ, РѕС‚РјРµС‡РµРЅРѕ Рё СЃРѕСЂР° (1-24 Р·Р°СЃС‚Р°.)",
    "РћРїС‚РёРјРёСЂРѕРІР°РЅРёРµ РЅР° РєР»СЋС‡РµРЅРёРµ РѕС‚РєСЂС‹С‚РѕРІР°С‚СЊ РєРѕРґРµР№: РЅРµС‚ РёР»Рё РЅР°С€Р»Р° (РёР»Рё СЃСЂРµРґС‹)",
    "РћРїС‚РёРјРёСЂРѕРІР°РЅРёРµ РІС‹РІРµ: РїСЂРѕРёСЃСЊ РєР»СЋС‡РµРЅРёРµ, РїСЂРѕРёРІРѕС‚СЊ РґР»СЏ РјРµР»Рµ, РѕС‚РјРµС‡РµРЅРѕ Рё СЃРѕСЂР° (1-24 Р·Р°СЃС‚Р°.)"
};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dataStore = new OfflineDataStore(this);
        contactsPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    String target = pendingCallTarget;
                    pendingCallTarget = null;
                    if (granted && target != null) {
                        findAndCallContact(target);
                    } else if (!granted) {
                        addMessage("Чтобы искать контакт по имени, разреши доступ к контактам "
                                + "или скажи номер телефона.", false);
                    }
                });
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && !isDestroyed() && textToSpeech != null) {
                speechEngineInitialized = true;
                configureTextToSpeech();
            }
        });
        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        btnMenu = findViewById(R.id.btnMenu);
        recyclerView = findViewById(R.id.recyclerView);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnVoice = findViewById(R.id.btnVoice);
        connectionStatus = findViewById(R.id.connectionStatus);
        chatScreen = findViewById(R.id.chatScreen);
        moduleContainer = findViewById(R.id.moduleContainer);
        btnTabChat = findViewById(R.id.btnTabChat);
        btnTabFinance = findViewById(R.id.btnTabFinance);
        btnTabGoals = findViewById(R.id.btnTabGoals);
        configureTabSwipes();

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);
        messageList.addAll(dataStore.getChatMessages());
        if (messageList.isEmpty()) {
            addMessage("Привет! Я L.I.R.A. Найду приложения по русскому или английскому названию, "
                    + "поставлю таймер или будильник и сохраню планы на устройстве.", false);
        } else {
            chatAdapter.notifyDataSetChanged();
            recyclerView.scrollToPosition(messageList.size() - 1);
        }
        updateConnectionStatus();

        btnTabChat.setOnClickListener(v -> showTab("chat"));
        btnTabFinance.setOnClickListener(v -> showTab("finance"));
        btnTabGoals.setOnClickListener(v -> showTab("goals"));
        showTab(savedInstanceState == null ? "chat"
                : savedInstanceState.getString(STATE_SELECTED_TAB, "chat"));

        // Открытие бокового меню по кнопке
        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Обработка выбора разделов в боковом меню
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == android.R.id.home || id == R.id.nav_chat) {
                showTab("chat");
                drawerLayout.closeDrawer(GravityCompat.START);
            } else if (id == R.id.nav_apps) {
                drawerLayout.closeDrawer(GravityCompat.START);
                showTab("chat");
                addMessage("Напиши 'открой [название]', и я найду любое приложение на телефоне!", false);
            } else if (id == R.id.nav_tools) {
                drawerLayout.closeDrawer(GravityCompat.START);
                showTab("chat");
                toggleFlashlight();
            } else if (id == R.id.nav_web) {
                drawerLayout.closeDrawer(GravityCompat.START);
                showTab("chat");
                addMessage("Напиши 'найди [запрос]', чтобы выполнить поиск в интернете.", false);
            } else if (id == R.id.nav_settings) {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, SettingsActivity.class));
            }
            return true;
        });

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                processUserMessage(text);
                etMessage.setText("");
            }
        });

        btnVoice.setOnClickListener(v -> startVoiceRecognition());
        handleActivityIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleActivityIntent(intent);
    }

    private void handleActivityIntent(Intent intent) {
        String command = intent.getStringExtra(LiraBackgroundService.EXTRA_VOICE_COMMAND);
        if (command != null && !command.trim().isEmpty()) {
            intent.removeExtra(LiraBackgroundService.EXTRA_VOICE_COMMAND);
            processUserMessage(command);
            return;
        }
        if (intent.getBooleanExtra(LiraBackgroundService.EXTRA_START_VOICE, false)) {
            intent.removeExtra(LiraBackgroundService.EXTRA_START_VOICE);
            if (getSharedPreferences("LiraPrefs", MODE_PRIVATE)
                    .getBoolean("background_voice_enabled", false)) {
                getSharedPreferences("LiraPrefs", MODE_PRIVATE).edit()
                        .putBoolean("background_voice_enabled", false).apply();
                stopService(new Intent(this, LiraBackgroundService.class));
                Toast.makeText(this,
                        "Фоновое прослушивание остановлено для голосового ввода. Его можно снова включить в настройках.",
                        Toast.LENGTH_LONG).show();
            }
            btnVoice.post(this::startVoiceRecognition);
        }
    }

    private boolean speakScreenText(String text) {
        if (getSharedPreferences("LiraPrefs", MODE_PRIVATE)
                .getBoolean("quiet_mode", false)
                || textToSpeech == null || !speechReady) {
            return false;
        }
        return textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "lira-screen-read") != TextToSpeech.ERROR;
    }

    private void showTab(String tab) {
        boolean changed = !tab.equals(selectedTab);
        selectedTab = tab;
        boolean isChat = "chat".equals(tab);
        chatScreen.setVisibility(isChat ? android.view.View.VISIBLE : android.view.View.GONE);
        moduleContainer.setVisibility(isChat ? android.view.View.GONE : android.view.View.VISIBLE);
        setTabSelected(btnTabChat, isChat);
        setTabSelected(btnTabFinance, "finance".equals(tab));
        setTabSelected(btnTabGoals, "goals".equals(tab));

        if (!isChat && changed) {
            androidx.fragment.app.Fragment fragment = "finance".equals(tab)
                    ? new FinanceFragment() : new GoalsFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.moduleContainer, fragment, tab)
                    .runOnCommit(() -> attachSwipeListener(moduleContainer))
                    .commit();
        }
    }

    private void configureTabSwipes() {
        tabGestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onFling(MotionEvent start, MotionEvent end,
                                           float velocityX, float velocityY) {
                        if (start == null || end == null) {
                            return false;
                        }
                        float distanceX = end.getX() - start.getX();
                        float distanceY = end.getY() - start.getY();
                        float threshold = 72 * getResources().getDisplayMetrics().density;
                        if (Math.abs(distanceX) < threshold
                                || Math.abs(distanceX) < Math.abs(distanceY) * 1.2f
                                || Math.abs(velocityX) < 150) {
                            return false;
                        }
                        String[] tabs = {"chat", "finance", "goals"};
                        int index = selectedTab.equals("chat") ? 0
                                : selectedTab.equals("finance") ? 1 : 2;
                        int nextIndex = distanceX < 0
                                ? Math.min(index + 1, tabs.length - 1)
                                : Math.max(index - 1, 0);
                        if (nextIndex == index) {
                            return false;
                        }
                        showTab(tabs[nextIndex]);
                        return true;
                    }
                });
        recyclerView.addOnItemTouchListener(
                new RecyclerView.SimpleOnItemTouchListener() {
                    @Override
                    public boolean onInterceptTouchEvent(RecyclerView view,
                                                         MotionEvent event) {
                        tabGestureDetector.onTouchEvent(event);
                        return false;
                    }
                });
    }

    private void attachSwipeListener(View view) {
        view.setOnTouchListener((target, event) -> {
            tabGestureDetector.onTouchEvent(event);
            return false;
        });
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                attachSwipeListener(group.getChildAt(index));
            }
        }
    }

    private void setTabSelected(android.view.View tab, boolean selected) {
        if (tab instanceof TextView) {
            ((TextView) tab).setTextColor(ContextCompat.getColor(this,
                    selected ? R.color.lira_accent : R.color.lira_muted));
        }
    }

    private void updateConnectionStatus() {
        connectionStatus.setText(hasInternetConnection()
                ? "Онлайн · локальные данные доступны"
                : "Офлайн · локальные данные доступны");
    }

    private boolean hasInternetConnection() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        for (Network network : connectivityManager.getAllNetworks()) {
            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(network);
            if (capabilities != null
                    && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager != null && networkCallback == null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    refreshConnectionStatus();
                }

                @Override
                public void onLost(Network network) {
                    refreshConnectionStatus();
                }

                @Override
                public void onCapabilitiesChanged(Network network,
                                                  NetworkCapabilities capabilities) {
                    refreshConnectionStatus();
                }
            };
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        }
    }

    @Override
    protected void onStop() {
        if (networkCallback != null) {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
            networkCallback = null;
        }
        super.onStop();
    }

    private void refreshConnectionStatus() {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed() && connectionStatus != null) {
                updateConnectionStatus();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        outState.putString(STATE_SELECTED_TAB, selectedTab);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (connectionStatus != null) {
            updateConnectionStatus();
        }
        if (textToSpeech != null) {
            configureTextToSpeech();
        }
    }

    private void configureTextToSpeech() {
        if (textToSpeech == null || !speechEngineInitialized) {
            return;
        }
        Locale russian = new Locale("ru", "RU");
        int languageStatus = textToSpeech.setLanguage(russian);
        boolean female = getSharedPreferences("LiraPrefs", MODE_PRIVATE)
                .getBoolean("voice_female", true);
        java.util.Set<Voice> availableVoices = textToSpeech.getVoices();
        List<Voice> offlineRussianVoices = availableVoices == null
                ? new ArrayList<>()
                : availableVoices.stream()
                .filter(voice -> russian.getLanguage()
                        .equals(voice.getLocale().getLanguage()))
                .filter(voice -> !voice.isNetworkConnectionRequired())
                .collect(java.util.stream.Collectors.toList());
        Voice selectedVoice = female
                ? offlineRussianVoices.stream()
                        .filter(voice -> voice.getName().toLowerCase(Locale.ROOT)
                                .matches(".*(female|woman|жен|irina|natasha|anna|elena).*"))
                        .findFirst().orElse(null)
                : null;
        if (selectedVoice == null && !offlineRussianVoices.isEmpty()) {
            selectedVoice = offlineRussianVoices.get(0);
        }
        if (selectedVoice != null) {
            textToSpeech.setVoice(selectedVoice);
        }
        textToSpeech.setPitch(female ? 1.0f : 0.92f);
        textToSpeech.setSpeechRate(0.9f);
        speechReady = selectedVoice != null
                && languageStatus != TextToSpeech.LANG_MISSING_DATA
                && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    private void addMessage(String text, boolean isUser) {
        addMessage(text, isUser, true);
    }

    private void addMessage(String text, boolean isUser, boolean speakResponse) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        ChatMessage message = new ChatMessage(text, isUser, time);
        dataStore.saveChatMessage(message);
        messageList.add(message);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
        if (!isUser && speakResponse) {
            speakAssistantResponse(text);
        }
    }

    private void speakAssistantResponse(String text) {
        android.content.SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        if (!prefs.getBoolean("speak_responses", false)
                || prefs.getBoolean("quiet_mode", false)) {
            return;
        }
        if (textToSpeech == null || !speechReady) {
            if (!speechFailureReported) {
                speechFailureReported = true;
                Toast.makeText(this,
                        "Русский синтез речи недоступен на устройстве. Установи голосовые данные в настройках Android.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, "lira-response")
                == TextToSpeech.ERROR && !speechFailureReported) {
            speechFailureReported = true;
            Toast.makeText(this, "Не удалось озвучить ответ.", Toast.LENGTH_LONG).show();
        }
    }

    private void startVoiceRecognition() {
        if (voiceRecognizer != null) {
            voiceRecognizer.stop();
            voiceRecognizer = null;
            btnVoice.setImageResource(android.R.drawable.ic_btn_speak_now);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_MICROPHONE);
            return;
        }
        int requestId = ++voiceRequestId;
        voiceRecognizer = new OfflineVoiceRecognizer(this, new OfflineVoiceRecognizer.Listener() {
            @Override
            public void onResult(String text) {
                if (requestId == voiceRequestId) {
                    voiceRecognizer = null;
                    btnVoice.setImageResource(android.R.drawable.ic_btn_speak_now);
                    processUserMessage(text);
                }
            }

            @Override
            public void onError(String message) {
                if (requestId == voiceRequestId) {
                    voiceRecognizer = null;
                    btnVoice.setImageResource(android.R.drawable.ic_btn_speak_now);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        });
        btnVoice.setImageResource(android.R.drawable.ic_media_pause);
        Toast.makeText(this, "Загружаю офлайн-распознавание. Нажмите ещё раз для отмены.",
                Toast.LENGTH_SHORT).show();
        voiceRecognizer.start();
    }

    private void processUserMessage(String text) {
        text = canonicalizeKnownAppName(text);
        showTab("chat");
        addMessage(text, true);
        String lower = text.toLowerCase(Locale.ROOT);

        if (isGoalSettingSessionActive) {
            // Process the user's response to the current goal-setting question.
            goalSettingResponses.append(text).append("; ");
            goalSettingStep++;
            if (goalSettingStep < GOAL_SETTING_STEPS) {
                addMessage(goalSettingQuestions[goalSettingStep], false);
            } else {
                // All questions answered, create the goal and reset session.
                String[] responses = goalSettingResponses.toString().split("; ");
                // We expect 5 responses: title, description, deadline, priority, category.
                // For simplicity, we'll store them as a single string or parse later.
                // We'll create a goal in the dataStore.
                // We'll use the first response as title, second as description, etc.
                String title = responses.length > 0 ? responses[0] : "";
                String description = responses.length > 1 ? responses[1] : "";
                String deadline = responses.length > 2 ? responses[2] : "";
                String priority = responses.length > 3 ? responses[3] : "";
                String category = responses.length > 4 ? responses[4] : "";
                dataStore.addGoal(title, deadline);
                addMessage("Цель успешно сохранена!", false);
                isGoalSettingSessionActive = false;
                goalSettingResponses = new StringBuilder();
                goalSettingStep = 0;
            }
            return;
        }

        PcCommandClient.Command pcCommand = PcCommandClient.parse(text);
        if (pcCommand != null) {
            sendPcCommand(pcCommand);
            return;
        }

        else if (startsWithCommand(lower, "цель", "задать цель", "новая цель", "goal", "set goal")) {
            isGoalSettingSessionActive = true;
            goalSettingResponses = new StringBuilder();
            goalSettingStep = 0;
            addMessage(goalSettingQuestions[0], false);
            return;
        }
        else if (startsWithCommand(lower, "инструкция", "помощь", "help", "commands")) {
            String helpText = "Доступные команды: \n" +
                    "• \"цель\" - начать постановку цели\n" +
                    "• \"инструкция\" - показать эту справку\n" +
                    "• \"очистить чат\" - очистить историю чата\n" +
                    "• \"меня зовут [имя]\" - представиться\n" +
                    "• \"таймер [время]\" - установить таймер\n" +
                    "• \"будильник [время]\" - установить будильник\n" +
                    "• \"найти [приложение]\" - открыть приложение\n" +
                    "• \"позвонить [контакт]\" - позвонить контакту\n" +
                    "• \"фонарик\" - включить/выключить фонарик\n" +
                    "• \"камера\" - сделать фото\n" +
                    "• \"заметка [текст]\" - создать голосовую заметку\n" +
                    "• \"поиск [запрос]\" - поиск в интернете\n" +
                    "• \"финансы [доход/расход] [сумма] [категория]\" - записать финансовую операцию";
            addMessage(helpText, false);
            return;
        }
        else if (startsWithCommand(lower, "очистить чат", "clear chat", "очистить историю")) {
            messageList.clear();
            chatAdapter.notifyDataSetChanged();
            addMessage("Чат очищен.", false);
            return;
        }
        else if (startsWithCommand(lower, "меня зовут", "my name is", "зовут")) {
            String name = removeCommandPrefix(text, "меня зовут", "my name is", "зовут");
            if (name.isEmpty()) {
                addMessage("Пожалуйста, укажите ваше имя после команды.", false);
            } else {
                userName = name;
                addMessage("Приятно познакомиться, " + name + "!", false);
            }
            return;
        }
        if (ScreenReadCommand.matches(text)) {
            String screenText = LiraScreenReaderService.lastExternalScreenText();
            if (screenText.isBlank()) {
                addMessage("Не нашла текст последнего экрана. Открой нужное приложение, "
                        + "вернись в L.I.R.A. и повтори. Для этой функции сначала включи "
                        + "«Настроить чтение экрана» в настройках.", false);
                return;
            }
            if (!LiraScreenReaderService.speakLastExternalScreen()
                    && !speakScreenText(screenText)) {
                addMessage("Не удалось запустить чтение. Проверь, что служба чтения экрана "
                        + "включена и в Android установлен русский офлайн-голос.", false);
                return;
            }
            addMessage("Читаю текст последнего открытого экрана.", false, false);
            return;
        }

        AndroidClockCommands.Result clockCommand = AndroidClockCommands.parse(text);
        if (clockCommand != null) {
            if (clockCommand.intent == null) {
                addMessage(clockCommand.message, false);
            } else if (clockCommand.intent.resolveActivity(getPackageManager()) == null) {
                addMessage("На устройстве не найдено приложение для таймеров и будильников.",
                        false);
            } else {
                try {
                    addMessage(clockCommand.message, false);
                    startActivity(clockCommand.intent);
                } catch (android.content.ActivityNotFoundException
                         | SecurityException | IllegalArgumentException exception) {
                    addMessage("Не удалось открыть системные «Часы». Проверь, что приложение "
                            + "«Часы» установлено и включено.", false);
                }
            }
            return;
        }

        TelegramDraftCommand.Draft telegramDraft = TelegramDraftCommand.parse(text);
        if (telegramDraft != null) {
            openTelegramDraft(telegramDraft);
            return;
        }

        String mediaResponse = MediaCommands.handle(this, text);
        if (mediaResponse != null) {
            addMessage(mediaResponse, false);
            return;
        }

        String callTarget = PhoneCallCommands.targetFrom(text);
        if (callTarget != null) {
            if (callTarget.isEmpty()) {
                addMessage("Скажи имя контакта или номер телефона.", false);
            } else if (PhoneCallCommands.isPhoneNumber(callTarget)) {
                confirmPhoneCall(callTarget, callTarget);
            } else {
                findAndCallContact(callTarget);
            }
            return;
        }

        List<FinanceVoiceParser.Entry> financeEntries = FinanceVoiceParser.parse(text);
        if (!financeEntries.isEmpty()) {
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            for (FinanceVoiceParser.Entry entry : financeEntries) {
                dataStore.addTransaction(entry.title, entry.amountCents, entry.income,
                        date, entry.category);
            }
            addMessage("Записала финансовые операции: " + financeEntries.size()
                    + ". Их можно проверить во вкладке «Финансы».", false);
        } else if (startsWithCommand(lower, "запиши заметку", "добавь заметку",
                "заметка", "запомни")) {
            String note = removeCommandPrefix(text, "запиши заметку", "добавь заметку",
                    "заметка", "запомни");
            if (note.isEmpty()) {
                addMessage("Произнеси текст заметки после команды «запиши заметку».", false);
            } else {
                String createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm",
                        Locale.US).format(new Date());
                dataStore.addVoiceNote("Заметка " + new SimpleDateFormat("d MMM, HH:mm",
                        new Locale("ru")).format(new Date()), note, createdAt);
                addMessage("Сохранила заметку на устройстве. Её можно прочитать здесь или во вкладке «Цели».",
                        false);
            }
        } else if (startsWithCommand(lower, "прочитай заметки", "покажи заметки",
                "мои заметки", "прочитай заметку")) {
            List<OfflineDataStore.VoiceNote> notes = dataStore.getVoiceNotes();
            if (notes.isEmpty()) {
                addMessage("Сохранённых заметок пока нет.", false);
            } else {
                StringBuilder result = new StringBuilder("Твои заметки:\n");
                for (int index = 0; index < notes.size(); index++) {
                    OfflineDataStore.VoiceNote note = notes.get(index);
                    result.append(index + 1).append(". ").append(note.title)
                            .append(": ").append(note.content).append('\n');
                }
                addMessage(result.toString().trim(), false);
            }
        } else if (lower.contains("фонарик") || lower.contains("torch")) {
            toggleFlashlight();
        } else if (lower.contains("камера") || lower.contains("фото")) {
            openCamera();
        } else if (startsWithCommand(lower, "найди", "найти", "поищи", "поиск", "search", "search for")) {
            String query = removeCommandPrefix(text, "найди", "найти", "поищи", "поиск",
                    "search for", "search");
            performWebSearch(query);
        } else if (startsWithCommand(lower, "открой", "открыть", "запусти", "запустить",
                "откройте", "запустите", "open", "launch", "start", "run")) {
            String appName = removeCommandPrefix(text, "открой", "открыть", "запусти",
                    "запустить", "откройте", "запустите",
                    "open", "launch", "start", "run")
                    .replaceFirst("(?iu)^(?:(?:мне|пожалуйста|приложение|app|the)\\s+)+", "");
            if (appName.isEmpty()) {
                addMessage("Назови приложение: например, «открой YouTube» или «открой WhatsApp».", false);
            } else {
                openAppByName(appName);
            }
        } else {
            addMessage("Я могу открыть приложение, найти информацию, поставить таймер или "
                    + "будильник, записать заметку, операцию или задачу. Для ПК скажи "
                    + "«открой на ПК…» или «поставь таймер на ПК…».", false);
        }
    }

    private void openTelegramDraft(TelegramDraftCommand.Draft draft) {
        String[] packages = {"org.telegram.messenger", "org.thunderdog.challegram"};
        for (String packageName : packages) {
            try {
                if (getPackageManager().getPackageInfo(packageName, 0) != null) {
                    Intent intent = TelegramDraftCommand.openTelegramDraft(draft, packageName);
                    startActivity(intent);
                    addMessage("Открываю Telegram и подготавливаю сообщение для «"
                            + draft.recipient + "». Проверь текст и нажми «Отправить».", false);
                    return;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                // Try the next supported Telegram client.
            } catch (ActivityNotFoundException exception) {
                addMessage("Не удалось открыть Telegram для создания черновика.", false);
                return;
            }
        }
        addMessage("Telegram не найден на устройстве.", false);
    }

    private void findAndCallContact(String target) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingCallTarget = target;
            contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS);
            return;
        }
        try {
            List<PhoneCallCommands.Contact> contacts =
                    PhoneCallCommands.findContacts(this, target);
            if (contacts.isEmpty()) {
                addMessage("Не нашла контакт «" + target
                        + "». Проверь имя в контактах или скажи номер.", false);
            } else if (contacts.size() == 1) {
                PhoneCallCommands.Contact contact = contacts.get(0);
                confirmPhoneCall(contact.name, contact.number);
            } else {
                String[] choices = new String[contacts.size()];
                for (int index = 0; index < contacts.size(); index++) {
                    PhoneCallCommands.Contact contact = contacts.get(index);
                    choices[index] = contact.name + " — " + contact.number;
                }
                new AlertDialog.Builder(this)
                        .setTitle("Выбери контакт")
                        .setItems(choices, (dialog, index) -> {
                            PhoneCallCommands.Contact contact = contacts.get(index);
                            confirmPhoneCall(contact.name, contact.number);
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
            }
        } catch (SecurityException exception) {
            addMessage("Android не разрешил читать контакты. Скажи номер телефона.", false);
        }
    }

    private void confirmPhoneCall(String name, String number) {
        new AlertDialog.Builder(this)
                .setTitle("Позвонить?")
                .setMessage(name + "\n" + number
                        + "\n\nОткрою приложение «Телефон» с этим номером. Нажми вызов там.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Открыть набор", (dialog, which) -> {
                    Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
                    try {
                        startActivity(dial);
                    } catch (ActivityNotFoundException exception) {
                        addMessage("На устройстве не найдено приложение для звонков.", false);
                    }
                })
                .show();
    }

    private String canonicalizeKnownAppName(String text) {
        if (!startsWithCommand(text.toLowerCase(Locale.ROOT),
                "открой", "открыть", "запусти", "запустить", "откройте", "запустите",
                "open", "launch", "start", "run")) {
            return text;
        }
        return text.replaceFirst("(?iu)^((?:открой|открыть|запусти|запустить|откройте|"
                + "запустите|open|launch|start|run)\\s+"
                + "(?:(?:(?:мне|пожалуйста|приложение|app|the)\\s+)*))"
                + "(?:клуб\\s+бери|клуббери|клаб\\s+бери|"
                + "club\\s+berry|clubberry)(?=\\s|$)", "$1Клуббери");
    }

    private void sendPcCommand(PcCommandClient.Command command) {
        if (command.query.isEmpty()) {
            String prompt = command.action.equals("search")
                    ? "Скажи, что найти в браузере на ПК."
                    : command.action.equals("timer")
                    ? "Назови длительность таймера на ПК."
                    : command.action.equals("alarm")
                    ? "Назови время будильника на ПК."
                    : "Назови программу, документ или папку на ПК.";
            addMessage(prompt, false);
            return;
        }
        addMessage("Отправляю команду на ПК…", false);
        pcCommandExecutor.execute(() -> {
            try {
                String response = PcCommandClient.execute(this, command);
                runOnUiThread(() -> addMessage(response, false));
            } catch (IOException exception) {
                runOnUiThread(() -> addMessage(
                        "Не удалось выполнить команду на ПК: " + exception.getMessage(), false));
            }
        });
    }

    private boolean startsWithCommand(String message, String... commands) {
        for (String command : commands) {
            if (message.equals(command) || message.startsWith(command + " ")) {
                return true;
            }
        }
        return false;
    }

    private String removeCommandPrefix(String message, String... commands) {
        for (String command : commands) {
            if (message.regionMatches(true, 0, command, 0, command.length())
                    && (message.length() == command.length()
                    || Character.isWhitespace(message.charAt(command.length())))) {
                return message.substring(command.length()).trim();
            }
        }
        return message.trim();
    }

    private void toggleFlashlight() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA);
            return;
        }
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cameraManager == null || cameraManager.getCameraIdList().length == 0) {
                addMessage("Фонарик недоступен на этом устройстве", false);
                return;
            }
            String cameraId = cameraManager.getCameraIdList()[0];
            isFlashlightOn = !isFlashlightOn;
            cameraManager.setTorchMode(cameraId, isFlashlightOn);
            addMessage(isFlashlightOn ? "Фонарик включен 🔦" : "Фонарик выключен", false);
        } catch (Exception e) {
            addMessage("Не удалось переключить фонарик", false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions,
                                           @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleFlashlight();
            } else {
                addMessage("Для управления фонариком нужно разрешение на камеру", false);
            }
        } else if (requestCode == REQUEST_CODE_MICROPHONE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition();
            } else {
                Toast.makeText(this, "Для голосового ввода нужен доступ к микрофону",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        voiceRequestId++;
        if (voiceRecognizer != null) {
            voiceRecognizer.stop();
            voiceRecognizer = null;
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        pcCommandExecutor.shutdownNow();
        dataStore.close();
        super.onDestroy();
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
            addMessage("Открываю камеру 📷", false);
        } else {
            addMessage("Камера недоступна", false);
        }
    }

    private void performWebSearch(String query) {
        if (query.isEmpty()) {
            addMessage("Напиши, что найти: например, «найди прогноз погоды».", false);
            return;
        }
        if (!hasInternetConnection()) {
            addMessage("Веб-поиск требует подключения к интернету. Остальные данные и функции приложения доступны офлайн.", false);
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
            addMessage("Ищу в интернете: " + query, false);
        } catch (android.content.ActivityNotFoundException exception) {
            addMessage("Не удалось открыть браузер для поиска. Проверь, установлен ли браузер.", false);
        }
    }

    private void openAppByName(String name) {
        PackageManager pm = getPackageManager();
        ResolveInfo bestMatch = AppNameMatcher.findLauncher(pm, name);
        if (bestMatch != null) {
            Intent launchIntent = pm.getLaunchIntentForPackage(bestMatch.activityInfo.packageName);
            if (launchIntent != null) {
                launchApp(pm, launchIntent, bestMatch.activityInfo.packageName);
                return;
            }
        }
        addMessage("Приложение \"" + name + "\" не найдено", false);
    }

    private void launchApp(PackageManager packageManager, Intent launchIntent, String packageName) {
        try {
            startActivity(launchIntent);
            addMessage("Запускаю " + packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)) + " 🚀", false);
        } catch (PackageManager.NameNotFoundException | android.content.ActivityNotFoundException exception) {
            addMessage("Не удалось запустить выбранное приложение. Возможно, оно было удалено.",
                    false);
        }
    }
}
