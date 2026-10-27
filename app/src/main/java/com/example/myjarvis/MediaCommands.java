package com.example.myjarvis;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

import androidx.annotation.Nullable;

import java.util.Locale;

final class MediaCommands {
    private MediaCommands() {
    }

    @Nullable
    static String handle(Context context, String phrase) {
        String command = phrase.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[!?.,]+$", "");
        if (matches(command, "следующий трек", "следующая песня", "дальше",
                "переключи трек", "переключи песню", "next", "next track")) {
            dispatch(context, KeyEvent.KEYCODE_MEDIA_NEXT);
            return "Переключаю на следующий трек.";
        }
        if (matches(command, "предыдущий трек", "предыдущая песня", "прошлый трек",
                "назад трек", "previous", "previous track")) {
            dispatch(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            return "Переключаю на предыдущий трек.";
        }
        if (matches(command, "пауза", "поставь на паузу", "поставь музыку на паузу",
                "останови музыку",
                "приостанови музыку", "pause", "pause music")) {
            dispatch(context, KeyEvent.KEYCODE_MEDIA_PAUSE);
            return "Ставлю музыку на паузу.";
        }
        if (matches(command, "продолжи музыку", "включи музыку", "возобнови музыку",
                "играй", "play", "resume")) {
            dispatch(context, KeyEvent.KEYCODE_MEDIA_PLAY);
            return "Продолжаю воспроизведение.";
        }
        if (matches(command, "переключи музыку", "воспроизведение", "плей пауза",
                "play pause", "play or pause")) {
            dispatch(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            return "Переключаю воспроизведение.";
        }
        if (matches(command, "поставь лайк", "лайкни", "лайкнуть", "мне нравится",
                "добавь в избранное",
                "like")) {
            return "У музыкальных приложений нет общей команды для лайка. "
                    + "Открой экран приложения и поставь отметку там.";
        }
        return null;
    }

    private static boolean matches(String command, String... aliases) {
        for (String alias : aliases) {
            if (command.equals(alias) || command.startsWith(alias + " ")) {
                return true;
            }
        }
        return false;
    }

    private static void dispatch(Context context, int keyCode) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        long now = System.currentTimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
    }
}
