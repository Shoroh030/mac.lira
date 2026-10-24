package com.lira.vmest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class WatchRoomSignalErrorTest {
    @Test
    public void explainsRlsForbiddenSignalsAndRecovery() {
        assertEquals("HTTP 403 (Supabase RLS): сервер отклонил сигнал. Выберите активную комнату этой пары и подключитесь к её ведущему.",
                WatchRoomSignalError.label("HTTP 403: {\"code\":\"42501\"}"));
    }

    @Test
    public void explainsExpiredSessionsWithoutEchoingBackendResponse() {
        assertEquals("HTTP 401: сессия Supabase недействительна. Войдите в свой аккаунт заново.",
                WatchRoomSignalError.label("HTTP 401: private auth response"));
    }
}
