package com.lira.vmest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class WatchRoomQrCodeTest {
    @Test
    public void encodesAndDecodesCanonicalRoomIds() {
        String roomId = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6";
        String payload = WatchRoomQrCode.encode(roomId);

        assertEquals("VMESTE:ROOM:" + roomId, payload);
        assertEquals(roomId, WatchRoomQrCode.decode(payload));
    }

    @Test
    public void rejectsPairInviteAndMalformedPayloads() {
        assertNull(WatchRoomQrCode.decode("VMESTE:PAIR:ABCD1234"));
        assertNull(WatchRoomQrCode.decode("VMESTE:ROOM:not-a-uuid"));
        assertNull(WatchRoomQrCode.decode(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesToEncodeNonUuidRoomIds() {
        WatchRoomQrCode.encode("room-123");
    }
}
