package com.lira.vmest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AvatarCachePolicyTest {
    @Test
    public void reusesOnlyTheMatchingDecodableLocalFile() {
        assertFalse(AvatarCachePolicy.shouldDownload(true, true, 128, true));
        assertTrue(AvatarCachePolicy.shouldDownload(false, true, 128, true));
        assertTrue(AvatarCachePolicy.shouldDownload(true, false, 0, false));
        assertTrue(AvatarCachePolicy.shouldDownload(true, true, 0, false));
        assertTrue(AvatarCachePolicy.shouldDownload(true, true, 128, false));
    }
}
