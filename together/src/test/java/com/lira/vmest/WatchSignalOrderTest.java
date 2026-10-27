package com.lira.vmest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class WatchSignalOrderTest {
    @Test
    public void processesSessionDescriptionsBeforeIceCandidates() {
        assertEquals(0, WatchSignalOrder.phase("offer"));
        assertEquals(0, WatchSignalOrder.phase("answer"));
        assertEquals(1, WatchSignalOrder.phase("ice"));
    }

    @Test
    public void keepsRoomEventsAfterNegotiationSignals() {
        assertEquals(2, WatchSignalOrder.phase("chat"));
        assertEquals(2, WatchSignalOrder.phase("playback"));
        assertEquals(2, WatchSignalOrder.phase("close"));
    }
}
