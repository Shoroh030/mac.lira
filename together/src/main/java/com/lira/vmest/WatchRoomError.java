package com.lira.vmest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WatchRoomError {
    private static final Pattern HTTP_ERROR = Pattern.compile("^HTTP (\\d{3}):\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Za-z0-9_-]{1,32})\"");

    private WatchRoomError() { }

    static String label(String message) {
        if (message == null || message.isEmpty()) {
            return "Не удалось выполнить действие с комнатой. Повторите попытку.";
        }

        Matcher httpError = HTTP_ERROR.matcher(message);
        if (!httpError.matches()) {
            if (message.contains("Exception") || message.startsWith("java.")
                    || message.startsWith("Unable to ")) {
                return "Не удалось связаться с сервером комнаты. Проверьте интернет и повторите попытку.";
            }
            return message;
        }

        int status = Integer.parseInt(httpError.group(1));
        String code = errorCode(httpError.group(2));
        String diagnostic = code.isEmpty() ? "HTTP " + status : "HTTP " + status + " (" + code + ")";
        if (status == 401) {
            return diagnostic + ": сессия Supabase недействительна. Войдите в свой аккаунт заново.";
        }
        if (status == 403 && "42501".equals(code)) {
            return diagnostic + ": сервер запретил операцию политикой доступа. Проверьте вход и подключение аккаунта к паре.";
        }
        if (status == 403) {
            return diagnostic + ": сервер запретил операцию. Проверьте вход и подключение аккаунта к паре.";
        }
        if (status == 404) {
            return diagnostic + ": сервер не нашёл ресурс комнаты. Администратору следует проверить доступность API и миграций.";
        }
        if (status >= 500) {
            return diagnostic + ": временная ошибка сервера. Повторите попытку позже.";
        }
        return diagnostic + ": сервер не выполнил запрос. Повторите попытку; если ошибка повторится, сообщите этот код.";
    }

    private static String errorCode(String body) {
        Matcher code = ERROR_CODE.matcher(body);
        return code.find() ? code.group(1) : "";
    }
}
