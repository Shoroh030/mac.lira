package com.lira.vmest;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class ThemePaletteTest {
    @Test
    public void lightThemeBrandAndSecondaryTextRemainReadable() {
        assertReadable(ThemePalette.forMode(false));
    }

    @Test
    public void darkThemeBrandAndSecondaryTextRemainReadable() {
        assertReadable(ThemePalette.forMode(true));
    }

    private static void assertReadable(ThemePalette palette) {
        assertTrue(ThemePalette.contrastRatio(palette.ink, palette.background) >= 4.5);
        assertTrue(ThemePalette.contrastRatio(palette.ink, palette.paper) >= 4.5);
        assertTrue(ThemePalette.contrastRatio(palette.ink, palette.pale) >= 4.5);
        assertTrue(ThemePalette.contrastRatio(palette.muted, palette.paper) >= 4.5);
        assertTrue(ThemePalette.contrastRatio(palette.muted, palette.pale) >= 4.5);
        assertTrue(ThemePalette.contrastRatio(palette.purple, palette.paper) >= 4.5);
        assertTrue(ThemePalette.contrastRatio(palette.purple, palette.pale) >= 4.5);
    }
}
