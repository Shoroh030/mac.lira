package com.lira.vmest;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class SharedLinkTest {
    @Test public void extractsHttpLinkFromSharedCaption() {
        assertEquals("https://example.com/post?id=5",
                SharedLink.firstHttpUrl("Посмотри: https://example.com/post?id=5."));
    }

    @Test public void ignoresNonWebAndMalformedLinks() {
        assertEquals("", SharedLink.firstHttpUrl("javascript:alert(1)"));
        assertEquals("", SharedLink.firstHttpUrl("https://"));
    }
}
