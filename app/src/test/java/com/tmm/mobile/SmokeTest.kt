package com.tmm.mobile

import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmokeTest {
    @Test
    fun mainActivityLaunchesWithoutException() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assert(!activity.isFinishing)
            }
        }
    }
}
