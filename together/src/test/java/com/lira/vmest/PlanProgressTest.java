package com.lira.vmest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class PlanProgressTest {
    @Test public void computesPlanCompletionPercentage() {
        assertEquals(0, PlanProgress.percent(0, 0));
        assertEquals(0, PlanProgress.percent(0, 4));
        assertEquals(25, PlanProgress.percent(1, 4));
        assertEquals(100, PlanProgress.percent(5, 5));
        assertEquals(100, PlanProgress.percent(8, 5));
    }
}
