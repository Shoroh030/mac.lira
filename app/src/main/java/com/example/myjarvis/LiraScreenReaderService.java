package com.example.myjarvis;

import android.accessibilityservice.AccessibilityService;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LiraScreenReaderService extends AccessibilityService {
    private static final int MAX_SCREEN_TEXT_LENGTH = 6000;
    private static volatile String lastExternalScreenText = "";
    private static volatile LiraScreenReaderService connectedService;

    private TextToSpeech textToSpeech;
    private boolean speechReady;

    static String lastExternalScreenText() {
        return lastExternalScreenText;
    }

    static boolean speakLastExternalScreen() {
        LiraScreenReaderService service = connectedService;
        if (service == null || !service.speechReady
                || service.textToSpeech == null || lastExternalScreenText.isBlank()) {
            return false;
        }
        if (service.getSharedPreferences("LiraPrefs", MODE_PRIVATE)
                .getBoolean("quiet_mode", false)) {
            return false;
        }
        return service.textToSpeech.speak(lastExternalScreenText,
                TextToSpeech.QUEUE_FLUSH, null, "lira-screen-read") != TextToSpeech.ERROR;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connectedService = this;
        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || connectedService != this) {
                return;
            }
            Locale russian = new Locale("ru", "RU");
            int languageStatus = textToSpeech.setLanguage(russian);
            java.util.Set<Voice> availableVoices = textToSpeech.getVoices();
            List<Voice> voices = availableVoices == null ? java.util.Collections.emptyList()
                    : availableVoices.stream()
                    .filter(voice -> russian.getLanguage()
                            .equals(voice.getLocale().getLanguage()))
                    .filter(voice -> !voice.isNetworkConnectionRequired())
                    .collect(java.util.stream.Collectors.toList());
            Voice femaleVoice = voices.stream()
                    .filter(voice -> voice.getName().toLowerCase(Locale.ROOT)
                            .matches(".*(female|woman|жен|irina|natasha|anna|elena).*"))
                    .findFirst().orElse(null);
            if (femaleVoice != null) {
                textToSpeech.setVoice(femaleVoice);
            } else if (!voices.isEmpty()) {
                textToSpeech.setVoice(voices.get(0));
            }
            textToSpeech.setPitch(1.08f);
            speechReady = !voices.isEmpty()
                    && languageStatus != TextToSpeech.LANG_MISSING_DATA
                    && languageStatus != TextToSpeech.LANG_NOT_SUPPORTED;
        });
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null
                || getPackageName().contentEquals(event.getPackageName())) {
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        Set<String> seen = new LinkedHashSet<>();
        Deque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty() && text.length() < MAX_SCREEN_TEXT_LENGTH) {
            AccessibilityNodeInfo node = pending.removeFirst();
            try {
                CharSequence nodeText = node.getText();
                if (nodeText == null || nodeText.length() == 0) {
                    nodeText = node.getContentDescription();
                }
                if (nodeText != null && nodeText.length() > 0) {
                    String value = nodeText.toString().trim();
                    if (!value.isEmpty() && seen.add(value)) {
                        if (text.length() > 0) {
                            text.append(". ");
                        }
                        text.append(value);
                    }
                }
                for (int index = 0; index < node.getChildCount(); index++) {
                    AccessibilityNodeInfo child = node.getChild(index);
                    if (child != null) {
                        pending.addLast(child);
                    }
                }
            } finally {
                node.recycle();
            }
        }
        if (text.length() > 0) {
            lastExternalScreenText = text.substring(0,
                    Math.min(text.length(), MAX_SCREEN_TEXT_LENGTH));
        } else {
            lastExternalScreenText = "";
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (connectedService == this) {
            connectedService = null;
            lastExternalScreenText = "";
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
    }
}
