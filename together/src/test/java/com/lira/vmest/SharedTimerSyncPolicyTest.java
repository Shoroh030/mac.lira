package com.lira.vmest;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SharedTimerSyncPolicyTest {
    @Test
    public void keepsLocalTimerUntilPendingUploadIsResolved() {
        assertFalse(SharedTimerSyncPolicy.shouldClearLocalTimer(false, true));
        assertTrue(SharedTimerSyncPolicy.shouldClearLocalTimer(false, false));
        assertFalse(SharedTimerSyncPolicy.shouldClearLocalTimer(true, false));
    }

    @Test
    public void refreshesTimerScreenOnlyForChangedStateAndWhenNotEditing() {
        assertTrue(SharedTimerSyncPolicy.shouldRenderAfterSync("[]", "[timer]", false));
        assertFalse(SharedTimerSyncPolicy.shouldRenderAfterSync("[timer]", "[timer]", false));
        assertFalse(SharedTimerSyncPolicy.shouldRenderAfterSync("[]", "[timer]", true));
    }
}
