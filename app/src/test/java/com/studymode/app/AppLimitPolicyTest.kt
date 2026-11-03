package com.studymode.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLimitPolicyTest {
    @Test
    fun warningTriggersAtConfiguredThresholdButBeforeLimit() {
        assertFalse(isAppLimitWarningDue(29 * 60_000L, 60, 50))
        assertTrue(isAppLimitWarningDue(30 * 60_000L, 60, 50))
        assertFalse(isAppLimitWarningDue(60 * 60_000L, 60, 50))
    }

    @Test
    fun warningIsDisabledForUnsupportedOrUnsetPercentages() {
        assertFalse(isAppLimitWarningDue(55 * 60_000L, 60, 0))
        assertFalse(isAppLimitWarningDue(55 * 60_000L, 60, 80))
    }

    @Test
    fun appIsRestrictedOnlyWhenBlockingIsEnabledAndLimitIsReached() {
        assertFalse(isAppDailyLimitReached(59 * 60_000L, 60, true))
        assertTrue(isAppDailyLimitReached(60 * 60_000L, 60, true))
        assertFalse(isAppDailyLimitReached(90 * 60_000L, 60, false))
    }
}
