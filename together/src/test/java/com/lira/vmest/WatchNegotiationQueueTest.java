package com.lira.vmest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class WatchNegotiationQueueTest {
    @Test
    public void ignoresDuplicateOffersWhileAndAfterAnswering() {
        WatchNegotiationQueue queue = new WatchNegotiationQueue();

        assertEquals("offer-a", queue.accept("offer-a", true));
        assertNull(queue.accept("offer-a", false));
        assertNull(queue.complete(true));
        assertNull(queue.accept("offer-a", true));
    }

    @Test
    public void appliesOnlyTheLatestQueuedOfferAfterTheCurrentAnswer() {
        WatchNegotiationQueue queue = new WatchNegotiationQueue();

        assertEquals("offer-a", queue.accept("offer-a", true));
        assertNull(queue.accept("offer-b", false));
        assertNull(queue.accept("offer-c", false));
        assertEquals("offer-c", queue.complete(true));
        assertNull(queue.complete(true));
    }

    @Test
    public void waitsUntilSignalingIsStableBeforeApplyingAnOffer() {
        WatchNegotiationQueue queue = new WatchNegotiationQueue();

        assertNull(queue.accept("offer-a", false));
        assertEquals("offer-a", queue.complete(true));
        assertNull(queue.complete(true));
    }
}
