package com.lira.vmest;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;

public final class SupabaseTimestampTest {
    @Test
    public void parsesFractionalUtcTimestamp() {
        Calendar expected = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        expected.clear();
        expected.set(2026, Calendar.JANUARY, 2, 3, 4, 5);
        expected.set(Calendar.MILLISECOND, 987);

        assertEquals(expected.getTimeInMillis(),
                SupabaseSync.parseTimestampMillis("2026-01-02T03:04:05.987654+00:00"));
    }

    @Test
    public void parsesTimestampWithoutFraction() {
        Calendar expected = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        expected.clear();
        expected.set(2026, Calendar.JANUARY, 2, 3, 4, 5);

        assertEquals(expected.getTimeInMillis(),
                SupabaseSync.parseTimestampMillis("2026-01-02T03:04:05Z"));
    }
}
