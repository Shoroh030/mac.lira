package com.lira.vmest;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class WatchPartyLayoutTest {
    @Test
    public void fullscreenExpandsPlayerAndBackRestoresControls() throws Exception {
        android.app.Instrumentation instrumentation =
                InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), WatchPartyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(WatchPartyActivity.EXTRA_ROOM_ID, UUID.randomUUID().toString())
                .putExtra(WatchPartyActivity.EXTRA_TITLE, "Тест комнаты");
        Activity activity = instrumentation.startActivitySync(intent);
        assertNotNull(activity);
        Throwable[] failure = {null};
        try {
            instrumentation.runOnMainSync(() -> {
                try {
                    View topPanel = (View) field(activity, "topPanel");
                    View controlsScroll = (View) field(activity, "controlsScroll");
                    FrameLayout playerContainer = (FrameLayout) field(activity, "playerContainer");
                    Method toggleFullscreen = WatchPartyActivity.class
                            .getDeclaredMethod("toggleFullscreenSafe");
                    toggleFullscreen.setAccessible(true);

                    toggleFullscreen.invoke(activity);
                    assertEquals(View.GONE, topPanel.getVisibility());
                    assertEquals(View.GONE, controlsScroll.getVisibility());
                    assertEquals(1f,
                            ((LinearLayout.LayoutParams) playerContainer.getLayoutParams()).weight,
                            0f);

                    activity.onBackPressed();
                    assertEquals(View.VISIBLE, topPanel.getVisibility());
                    assertEquals(View.VISIBLE, controlsScroll.getVisibility());
                    assertEquals(3f,
                            ((LinearLayout.LayoutParams) playerContainer.getLayoutParams()).weight,
                            0f);
                } catch (Throwable error) {
                    failure[0] = error;
                }
            });
            if (failure[0] != null) throw new AssertionError(failure[0]);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
