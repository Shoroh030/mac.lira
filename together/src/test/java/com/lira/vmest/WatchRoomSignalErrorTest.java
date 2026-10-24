package com.lira.vmest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class WatchRoomSignalErrorTest {
    @Test
    public void explainsRlsForbiddenSignalsAndRecovery() {
        assertEquals("HTTP 403 (42501) (Supabase RLS): сигнал отклонён. Политика требует активную комнату той же пары и sender_id текущего аккаунта. Проверьте пару и выбранную комнату; если условия верны, администратору нужно сверить применённую RLS-политику.",
                WatchRoomSignalError.label("HTTP 403: {\"code\":\"42501\"}"));
    }

    @Test
    public void explainsGenericForbiddenSignalWithoutEchoingBackendResponse() {
        assertEquals("HTTP 403 (Supabase RLS): сигнал отклонён. Проверьте, что участники в одной паре, комната активна, а аккаунты разные.",
                WatchRoomSignalError.label("HTTP 403: {\"message\":\"private response\"}"));
    }

    @Test
    public void explainsExpiredSessionsWithoutEchoingBackendResponse() {
        assertEquals("HTTP 401: сессия Supabase недействительна. Войдите в свой аккаунт заново.",
                WatchRoomSignalError.label("HTTP 401: private auth response"));
    }
}
