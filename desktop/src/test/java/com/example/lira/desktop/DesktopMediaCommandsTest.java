package com.example.lira.desktop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DesktopMediaCommandsTest {
    @Test
    void parsesPlaybackCommands() {
        assertEquals("pause", DesktopMediaCommands.normalizeCommand("пауза"));
        assertEquals("pause", DesktopMediaCommands.normalizeCommand("поставь музыку на паузу"));
        assertEquals("play", DesktopMediaCommands.normalizeCommand("продолжи музыку"));
        assertEquals("next", DesktopMediaCommands.normalizeCommand("следующий трек"));
        assertEquals("previous", DesktopMediaCommands.normalizeCommand("предыдущая песня"));
        assertEquals("play-pause", DesktopMediaCommands.normalizeCommand("переключи музыку"));
    }

    @Test
    void reportsLikesAsUnsupportedAcrossPlayers() {
        assertEquals("like", DesktopMediaCommands.normalizeCommand("поставь лайк"));
        assertNull(DesktopMediaCommands.normalizeCommand("открой калькулятор"));
    }
}
