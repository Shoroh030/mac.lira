package com.lira.vmest;

import android.content.Context;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class WatchPlayerRuntimeSmokeTest {
    private static final long PREPARE_TIMEOUT_SECONDS = 30;
    private static final String TEST_MP4 =
            "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4";
    private static final String TEST_HLS =
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";

    @Test
    public void preparesPublicMp4WithoutStartingPlayback() throws Exception {
        assertVideoSourcePrepares(TEST_MP4);
    }

    @Test
    public void preparesPublicHlsWithoutStartingPlayback() throws Exception {
        assertVideoSourcePrepares(TEST_HLS);
    }

    private void assertVideoSourcePrepares(String url) throws Exception {
        android.app.Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext().getApplicationContext();
        AtomicReference<ExoPlayer> playerReference = new AtomicReference<>();
        CountDownLatch prepared = new CountDownLatch(1);
        AtomicReference<PlaybackException> failure = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            ExoPlayer player = new ExoPlayer.Builder(context).build();
            playerReference.set(player);
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) prepared.countDown();
                }

                @Override public void onPlayerError(PlaybackException error) {
                    failure.set(error);
                    prepared.countDown();
                }
            });
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
        });
        try {
            assertTrue("Timed out preparing public video: " + url,
                    prepared.await(PREPARE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull("Media3 could not prepare the public video: " + failure.get(),
                    failure.get());
            AtomicBoolean playWhenReady = new AtomicBoolean(true);
            instrumentation.runOnMainSync(() -> playWhenReady.set(playerReference.get().getPlayWhenReady()));
            assertTrue("Playback started before user input", !playWhenReady.get());
        } finally {
            instrumentation.runOnMainSync(() -> playerReference.get().release());
        }
    }
}
