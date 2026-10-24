package com.lira.vmest;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public final class WatchRoomSafetyTest {
    private static final String TAG = "WatchRoomSafety";
    private static final String TEST_TITLE_PREFIX = "VMESTE_TEST_WATCH_";

    @Test
    public void reportOnlySanitizedActiveRoomSummary() throws Exception {
        SupabaseSync sync = new SupabaseSync(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        boolean signedIn = sync.signedIn();
        boolean pairLinked = !sync.coupleId().isEmpty();
        Log.i(TAG, "local-account-session=" + signedIn + ", pair-linked=" + pairLinked);
        assumeTrue("Pair session unavailable",
                sync.configured() && signedIn && pairLinked);
        Method request = SupabaseSync.class.getDeclaredMethod(
                "requestValue", String.class, String.class, JSONObject.class, boolean.class);
        request.setAccessible(true);
        Object result;
        try {
            result = request.invoke(sync, "GET",
                    "/rest/v1/watch_rooms?select=id,title&couple_id=eq." + sync.coupleId() + "&state=eq.active",
                    null, true);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        }
        JSONArray rooms = (JSONArray) result;
        assertNotNull("Active room query did not return a row array", rooms);
        boolean allExplicitTestRooms = rooms.length() > 0;
        for (int i = 0; i < rooms.length(); i++) {
            String title = rooms.optJSONObject(i) == null ? "" : rooms.optJSONObject(i).optString("title");
            allExplicitTestRooms &= title.startsWith(TEST_TITLE_PREFIX);
        }
        Log.i(TAG, "active-room-count=" + rooms.length()
                + ", all-explicitly-test-marked=" + allExplicitTestRooms);
    }
}
