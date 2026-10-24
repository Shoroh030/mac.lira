package com.lira.vmest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public final class WatchPlayerSourceTest {
    @Test
    public void recognizesYouTubeWatchAndShortLinks() {
        WatchPlayerSource watch = WatchPlayerSource.parse(
                "https://www.youtube.com/watch?v=M7lc1UVf-VE&t=10");
        WatchPlayerSource shortLink = WatchPlayerSource.parse(
                "https://youtu.be/M7lc1UVf-VE?si=share");

        assertEquals(WatchPlayerSource.Kind.YOUTUBE, watch.kind);
        assertEquals("M7lc1UVf-VE", watch.youtubeVideoId);
        assertEquals(WatchPlayerSource.Kind.YOUTUBE, shortLink.kind);
        assertEquals("M7lc1UVf-VE", shortLink.youtubeVideoId);
    }

    @Test
    public void recognizesYouTubeEmbedAndLiveLinks() {
        assertEquals("M7lc1UVf-VE", WatchPlayerSource.parse(
                "https://www.youtube-nocookie.com/embed/M7lc1UVf-VE").youtubeVideoId);
        assertEquals("M7lc1UVf-VE", WatchPlayerSource.parse(
                "https://youtube.com/live/M7lc1UVf-VE").youtubeVideoId);
    }

    @Test
    public void doesNotTreatNonVideoYouTubePagesAsSynchronizedVideos() {
        WatchPlayerSource source = WatchPlayerSource.parse("https://www.youtube.com/channel/test");

        assertEquals(WatchPlayerSource.Kind.WEBSITE, source.kind);
        assertNull(source.youtubeVideoId);
    }

    @Test
    public void recognizesDirectProgressiveAndHlsVideoUrls() {
        assertEquals(WatchPlayerSource.Kind.DIRECT_VIDEO,
                WatchPlayerSource.parse("https://cdn.example/video.mp4?token=abc").kind);
        assertEquals(WatchPlayerSource.Kind.DIRECT_VIDEO,
                WatchPlayerSource.parse("https://cdn.example/live.m3u8?token=abc").kind);
        assertEquals(WatchPlayerSource.Kind.DIRECT_VIDEO,
                WatchPlayerSource.parse("https://cdn.example/video.webm").kind);
    }

    @Test
    public void keepsOtherHttpsSitesAsUnsynchronizedWebsites() {
        WatchPlayerSource source = WatchPlayerSource.parse("https://kinogo.example/watch/123");

        assertEquals(WatchPlayerSource.Kind.WEBSITE, source.kind);
    }

    @Test
    public void rejectsInsecureOrCredentialBearingLinks() {
        assertThrows(IllegalArgumentException.class,
                () -> WatchPlayerSource.parse("http://cdn.example/video.mp4"));
        assertThrows(IllegalArgumentException.class,
                () -> WatchPlayerSource.parse("https://user:pass@cdn.example/video.mp4"));
        assertThrows(IllegalArgumentException.class,
                () -> WatchPlayerSource.parse("javascript:alert(1)"));
    }
}
