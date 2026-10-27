package com.example.lira.desktop;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClockCommandParserTest {
    @Test
    void parsesMultiUnitTimerDuration() {
        ClockCommandParser.Command command =
                ClockCommandParser.parse("поставь таймер на 2 минуты 30 секунд");

        assertNotNull(command);
        assertNull(command.error());
        assertEquals(150, command.seconds());
    }

    @Test
    void parsesSpokenNumberWordsForTimersAndAlarms() {
        ClockCommandParser.Command timer =
                ClockCommandParser.parse("поставь таймер на пять минут");
        ClockCommandParser.Command alarm =
                ClockCommandParser.parse("поставь будильник на семь тридцать");

        assertEquals(300, timer.seconds());
        assertEquals(LocalTime.of(7, 30), alarm.alarmTime());
    }

    @Test
    void parsesSpokenAlarmTime() {
        ClockCommandParser.Command command =
                ClockCommandParser.parse("поставь будильник на 7 часов 15 минут");

        assertNotNull(command);
        assertNull(command.error());
        assertEquals(LocalTime.of(7, 15), command.alarmTime());
    }

    @Test
    void parsesRemoteTimerAndAlarmRequests() {
        ClockCommandParser.Command timer =
                ClockCommandParser.parseRemote("timer", "на пять минут");
        ClockCommandParser.Command alarm =
                ClockCommandParser.parseRemote("alarm", "на 07:30");

        assertEquals(300, timer.seconds());
        assertEquals(LocalTime.of(7, 30), alarm.alarmTime());
    }

    @Test
    void rejectsTimerWithoutUnitsAndAlarmWithoutTime() {
        assertNotNull(ClockCommandParser.parse("поставь таймер").error());
        assertNotNull(ClockCommandParser.parse("поставь будильник").error());
    }

    @Test
    void supportsAnHourOnlyAlarmTime() {
        ClockCommandParser.Command command =
                ClockCommandParser.parse("поставь будильник на 7");

        assertEquals(LocalTime.of(7, 0), command.alarmTime());
    }
}
