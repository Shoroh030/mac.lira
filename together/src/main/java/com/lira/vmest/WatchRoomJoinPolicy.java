package com.lira.vmest;

final class WatchRoomJoinPolicy {
    private WatchRoomJoinPolicy() { }

    static String error(String state, String roomCoupleId, String hostId, String coupleId, String userId) {
        if (state == null || state.isEmpty()) return "Активная комната с таким кодом не найдена";
        if (!"active".equals(state))
            return "Эта комната уже завершена. Попросите партнёра создать новую.";
        if (coupleId == null || coupleId.isEmpty()
                || !coupleId.equals(roomCoupleId))
            return "Комната принадлежит другой паре";
        if (userId == null || userId.isEmpty())
            return "Не удалось определить аккаунт. Войдите заново.";
        if (hostId == null || hostId.isEmpty())
            return "У комнаты отсутствует владелец. Попросите партнёра создать новую.";
        if (userId.equals(hostId))
            return "Для общей комнаты нужны два разных аккаунта: на этом телефоне открыт аккаунт создателя.";
        return null;
    }
}
