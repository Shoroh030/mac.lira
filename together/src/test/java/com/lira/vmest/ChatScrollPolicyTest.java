package com.lira.vmest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatScrollPolicyTest {
    @Test
    public void followsMessagesWhenAlreadyAtLatest() {
        ChatScrollPolicy policy = new ChatScrollPolicy();

        assertTrue(policy.onScroll(452, 500, 980, 32));
        assertTrue(policy.onMessagesAdded(2, false));
        assertEquals(0, policy.pendingIncomingCount());
    }

    @Test
    public void preservesHistoryAndCountsIncomingMessagesWhenScrolledUp() {
        ChatScrollPolicy policy = new ChatScrollPolicy();
        policy.onScroll(100, 500, 980, 32);

        assertFalse(policy.onMessagesAdded(2, false));
        assertFalse(policy.onMessagesAdded(1, false));
        assertEquals(3, policy.pendingIncomingCount());
        assertFalse(policy.isFollowingLatest());
    }

    @Test
    public void ownMessageReturnsToLatestAndClearsUnreadCount() {
        ChatScrollPolicy policy = new ChatScrollPolicy();
        policy.onScroll(100, 500, 980, 32);
        policy.onMessagesAdded(2, false);

        assertTrue(policy.onMessagesAdded(0, true));
        assertTrue(policy.isFollowingLatest());
        assertEquals(0, policy.pendingIncomingCount());
    }

    @Test
    public void scrollingBackToBottomClearsUnreadCount() {
        ChatScrollPolicy policy = new ChatScrollPolicy();
        policy.onScroll(100, 500, 980, 32);
        policy.onMessagesAdded(2, false);

        assertTrue(policy.onScroll(460, 500, 980, 32));
        assertEquals(0, policy.pendingIncomingCount());
    }
}
