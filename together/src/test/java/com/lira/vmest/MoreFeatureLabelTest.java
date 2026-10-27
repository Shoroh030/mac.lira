package com.lira.vmest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class MoreFeatureLabelTest {
    @Test
    public void preservesTitleAfterSingleCodeUnitIcon() {
        MoreFeatureLabel label = MoreFeatureLabel.parse("⏳  Капсула времени");

        assertEquals("⏳", label.icon);
        assertEquals("Капсула времени", label.title);
    }

    @Test
    public void preservesEmojiIconAndTitle() {
        MoreFeatureLabel label = MoreFeatureLabel.parse("📎  Файлы");

        assertEquals("📎", label.icon);
        assertEquals("Файлы", label.title);
    }
}
