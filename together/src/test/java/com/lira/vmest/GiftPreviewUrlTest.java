package com.lira.vmest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class GiftPreviewUrlTest {
    @Test
    public void acceptsPublicHttpAndHttpsLinks() {
        assertEquals("https://shop.example.org/item?id=3",
                GiftPreviewUrl.normalizePublicHttpUrl("https://shop.example.org/item?id=3"));
        assertEquals("http://shop.example.org/",
                GiftPreviewUrl.normalizePublicHttpUrl("http://shop.example.org/"));
    }

    @Test
    public void rejectsNonWebAndCredentialBearingLinks() {
        assertEquals("", GiftPreviewUrl.normalizePublicHttpUrl("file:///data/local/tmp/app.apk"));
        assertEquals("", GiftPreviewUrl.normalizePublicHttpUrl("content://private/item"));
        assertEquals("", GiftPreviewUrl.normalizePublicHttpUrl("javascript:alert(1)"));
        assertEquals("", GiftPreviewUrl.normalizePublicHttpUrl("https://user:pass@shop.example.org/"));
    }

    @Test
    public void rejectsLocalAndIpLiteralHosts() {
        assertEquals("", GiftPreviewUrl.normalizePublicHttpUrl("http://localhost/"));
        assertEquals("", GiftPreviewUrl.normalizePublicHttpUrl("http://printer.local/"));
        assertEquals("", GiftPreviewUrl.normalizePublicHttpUrl("http://192.168.1.10/"));
        assertEquals("", GiftPreviewUrl.normalizePublicHttpUrl("http://127.0.0.1/"));
        assertEquals("", GiftPreviewUrl.normalizePublicHttpUrl("http://127.1/"));
        assertEquals("", GiftPreviewUrl.normalizePublicHttpUrl("http://0x7f.0x1/"));
        assertEquals("", GiftPreviewUrl.normalizePublicHttpUrl("https://intranet/"));
    }
}
