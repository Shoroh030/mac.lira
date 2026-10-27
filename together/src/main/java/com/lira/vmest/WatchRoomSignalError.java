package com.lira.vmest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class WatchRoomSignalError {
    private static final Pattern HTTP_ERROR = Pattern.compile("^HTTP (\\d{3}):\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern ERROR_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Za-z0-9_-]{1,32})\"");

    private WatchRoomSignalError() { }

    static String label(String message) {
        if (message == null || message.isEmpty()) return "Неизвестная ошибка";
        Matcher httpError = HTTP_ERROR.matcher(message);
        if (!httpError.matches()) {
            if (message.contains("Exception") || message.startsWith("java.")
                    || message.startsWith("Unable to ")) {
                return "Не удалось связаться с сервером комнаты. Проверьте интернет и повторите попытку.";
            }
            return message;
        }

        int status = Integer.parseInt(httpError.group(1));
        Matcher errorCode = ERROR_CODE.matcher(httpError.group(2));
        String code = errorCode.find() ? errorCode.group(1) : "";
        String diagnostic = code.isEmpty() ? "HTTP " + status : "HTTP " + status + " (" + code + ")";
        if (status == 403 && "42501".equals(code)) {
            return diagnostic + " (Supabase RLS): сигнал отклонён. Политика требует активную комнату той же пары и sender_id текущего аккаунта. Проверьте пару и выбранную комнату; если условия верны, администратору нужно сверить применённую RLS-политику.";
        }
        if (status == 403) {
            return diagnostic + " (Supabase RLS): сигнал отклонён. Проверьте, что участники в одной паре, комната активна, а аккаунты разные.";
        }
        if (status == 401) {
            return diagnostic + ": сессия Supabase недействительна. Войдите в свой аккаунт заново.";
        }
        return diagnostic + ": сервер не принял сигнал. Повторите попытку; если ошибка повторится, сообщите этот код.";
    }
}
