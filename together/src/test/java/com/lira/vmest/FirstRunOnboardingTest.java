package com.lira.vmest;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FirstRunOnboardingTest {
    @Test
    public void showsBeforeRegistrationForFreshSignedOutInstall() {
        assertTrue(FirstRunOnboarding.shouldShow(false, false, false));
    }

    @Test
    public void doesNotRepeatAfterDismissalOrCompletion() {
        assertFalse(FirstRunOnboarding.shouldShow(false, true, false));
    }

    @Test
    public void skipsOnboardingForSignedInUsersAndIncomingShares() {
        assertFalse(FirstRunOnboarding.shouldShow(true, false, false));
        assertFalse(FirstRunOnboarding.shouldShow(false, false, true));
    }
}
