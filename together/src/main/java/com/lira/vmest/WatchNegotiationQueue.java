package com.lira.vmest;

final class WatchNegotiationQueue {
    private String lastOffer = "";
    private String pendingOffer = "";
    private boolean processing;

    synchronized String accept(String sdp, boolean signalingStable) {
        if (sdp == null || sdp.isEmpty()
                || sdp.equals(lastOffer) || sdp.equals(pendingOffer)) return null;
        if (processing || !signalingStable) {
            pendingOffer = sdp;
            return null;
        }
        processing = true;
        lastOffer = sdp;
        return sdp;
    }

    synchronized String complete(boolean signalingStable) {
        processing = false;
        if (!signalingStable) {
            pendingOffer = "";
            return null;
        }
        if (pendingOffer.isEmpty() || pendingOffer.equals(lastOffer)) {
            pendingOffer = "";
            return null;
        }
        lastOffer = pendingOffer;
        pendingOffer = "";
        processing = true;
        return lastOffer;
    }
}
