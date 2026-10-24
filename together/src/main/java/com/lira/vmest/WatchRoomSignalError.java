package com.lira.vmest;

final class WatchRoomSignalError {
    private WatchRoomSignalError() { }

    static String label(String message) {
        if (message == null || message.isEmpty()) return "Неизвестная ошибка";
        if (message.startsWith("HTTP 403:"))
            return "HTTP 403 (Supabase RLS): сервер отклонил сигнал. Выберите активную комнату этой пары и подключитесь к её ведущему.";
        if (message.startsWith("HTTP 401:"))
            return "HTTP 401: сессия Supabase недействительна. Войдите в свой аккаунт заново.";
        int separator = message.indexOf(':');
        if (message.startsWith("HTTP ") && separator > 0)
            return message.substring(0, separator);
        return message;
    }
}
