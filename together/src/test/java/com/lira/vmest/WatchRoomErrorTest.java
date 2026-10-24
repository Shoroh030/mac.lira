package com.lira.vmest;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class WatchRoomErrorTest {
    @Test
    public void explainsRoomCreationPermissionFailureWithoutEchoingBackendPayload() {
        assertEquals("HTTP 403 (42501): сервер запретил операцию политикой доступа. Проверьте вход и подключение аккаунта к паре.",
                WatchRoomError.label("HTTP 403: {\"code\":\"42501\",\"message\":\"blocked for user https://private.example/token\"}"));
    }

    @Test
    public void explainsMissingBackendResourceWithoutExposingResponse() {
        assertEquals("HTTP 404 (PGRST205): сервер не нашёл ресурс комнаты. Администратору следует проверить доступность API и миграций.",
                WatchRoomError.label("HTTP 404: {\"code\":\"PGRST205\",\"message\":\"table with private id not found\"}"));
    }

    @Test
    public void givesSafeRecoveryForExpiredSessionAndNetworkErrors() {
        assertEquals("HTTP 401 (PGRST301): сессия Supabase недействительна. Войдите в свой аккаунт заново.",
                WatchRoomError.label("HTTP 401: {\"code\":\"PGRST301\",\"message\":\"private token\"}"));
        assertEquals("Не удалось связаться с сервером комнаты. Проверьте интернет и повторите попытку.",
                WatchRoomError.label("UnknownHostException: private.example"));
    }

    @Test
    public void preservesSafeRoomPolicyMessages() {
        assertEquals("Комната уже закрыта", WatchRoomError.label("Комната уже закрыта"));
    }
}
