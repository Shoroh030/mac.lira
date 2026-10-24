package com.lira.vmest;

public final class PlanProgress {
    private PlanProgress() {}

    public static int percent(int completed, int total) {
        if (total <= 0 || completed <= 0) return 0;
        return Math.min(100, (int) ((long) completed * 100 / total));
    }
}
