package com.lira.vmest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void acceptsRoomCodeLinkAndSharedInvitationText() {
        String roomId = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6";
        String shareText = WatchRoomQrCode.shareText(roomId);

        assertEquals("vmeste://room/" + roomId, WatchRoomQrCode.link(roomId));
        assertTrue(shareText.startsWith("Подключайтесь к комнате просмотра I'm Here:\n"));
        assertTrue(shareText.contains("Код: VMESTE:ROOM:" + roomId));
        assertEquals(roomId, WatchRoomQrCode.decodeInvitation(shareText));
        assertEquals(roomId, WatchRoomQrCode.decodeInvitation(WatchRoomQrCode.link(roomId)));
        assertEquals(roomId, WatchRoomQrCode.decodeInvitation(roomId));
    }

    @Test
    public void rejectsUnrelatedOrMalformedInvitationLinks() {
        assertNull(WatchRoomQrCode.decodeInvitation("https://example.com/room/not-a-uuid"));
        assertNull(WatchRoomQrCode.decodeInvitation("vmeste://other/f81d4fae-7dec-11d0-a765-00a0c91e6bf6"));
        assertNull(WatchRoomQrCode.decodeInvitation("VMESTE:ROOM:invalid"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesToEncodeNonUuidRoomIds() {
        WatchRoomQrCode.encode("room-123");
    }
}
