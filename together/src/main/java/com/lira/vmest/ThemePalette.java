package com.lira.vmest;

final class ThemePalette {
    final int background;
    final int paper;
    final int ink;
    final int muted;
    final int purple;
    final int pale;
    final int line;

    private ThemePalette(int background, int paper, int ink, int muted,
            int purple, int pale, int line) {
        this.background = background;
        this.paper = paper;
        this.ink = ink;
        this.muted = muted;
        this.purple = purple;
        this.pale = pale;
        this.line = line;
    }

    static ThemePalette forMode(boolean dark) {
        if (dark) {
            return new ThemePalette(0xFF211D25, 0xFF302A35, 0xFFF2EDF5, 0xFFB8ACBF,
                    0xFFD0B3E9, 0xFF403648, 0xFF504657);
        }
        return new ThemePalette(0xFFFBF8FC, 0xFFFFFFFF, 0xFF302B35, 0xFF6E6575,
                0xFF8158A5, 0xFFF0E8F6, 0xFFE9E1ED);
    }

    static double contrastRatio(int first, int second) {
        double lighter = Math.max(relativeLuminance(first), relativeLuminance(second));
        double darker = Math.min(relativeLuminance(first), relativeLuminance(second));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(int color) {
        double red = linearize((color >> 16) & 0xFF);
        double green = linearize((color >> 8) & 0xFF);
        double blue = linearize(color & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearize(int component) {
        double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
