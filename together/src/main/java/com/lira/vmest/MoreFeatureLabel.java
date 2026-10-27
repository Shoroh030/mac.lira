package com.lira.vmest;

final class MoreFeatureLabel {
    final String icon;
    final String title;

    private MoreFeatureLabel(String icon, String title) {
        this.icon = icon;
        this.title = title;
    }

    static MoreFeatureLabel parse(String value) {
        if (value == null) return new MoreFeatureLabel("", "");
        int separator = value.indexOf("  ");
        if (separator < 0) return new MoreFeatureLabel("", value.trim());
        return new MoreFeatureLabel(
                value.substring(0, separator).trim(),
                value.substring(separator + 2).trim());
    }
}
