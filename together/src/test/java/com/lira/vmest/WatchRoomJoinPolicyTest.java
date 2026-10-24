package com.lira.vmest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class WatchRoomJoinPolicyTest {
    @Test
    public void rejectsJoiningFromTheSameAccountThatCreatedTheRoom() {
        assertEquals("Для общей комнаты нужны два разных аккаунта: на этом телефоне открыт аккаунт создателя.",
                WatchRoomJoinPolicy.error("active", "couple-1", "user-1", "couple-1", "user-1"));
    }

    @Test
    public void rejectsRoomsOutsideTheCurrentCouple() {
        assertEquals("Комната принадлежит другой паре",
                WatchRoomJoinPolicy.error("active", "couple-2", "user-1", "couple-1", "user-2"));
    }

    @Test
    public void permitsAnActiveRoomFromAnotherMemberOfTheSameCouple() {
        assertNull(WatchRoomJoinPolicy.error(
                "active", "couple-1", "user-1", "couple-1", "user-2"));
    }

    @Test
    public void rejectsClosedRoomsAndMalformedRoomOwnership() {
        assertEquals("Эта комната уже завершена. Попросите партнёра создать новую.",
                WatchRoomJoinPolicy.error("closed", "couple-1", "user-1", "couple-1", "user-2"));
        assertEquals("У комнаты отсутствует владелец. Попросите партнёра создать новую.",
                WatchRoomJoinPolicy.error("active", "couple-1", "", "couple-1", "user-2"));
    }
}
