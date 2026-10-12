package com.tmm.mobile.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppPrefsTest {
    private lateinit var prefs: AppPrefs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, "test_prefs.preferences_pb") },
        )
        prefs = AppPrefs(dataStore)
    }

    @Test fun defaults_matchPlanDefaults() = runTest {
        assertEquals(false, prefs.onboardingDone.first())
        assertEquals(false, prefs.wifiOnly.first())
        assertEquals(Retention.NEVER, prefs.retention.first())
    }

    @Test fun driveFileIds_roundTripsAsJsonMap() = runTest {
        prefs.setDriveFileIds(mapOf("root" to "id1", "jobs" to "id2"))
        assertEquals(mapOf("root" to "id1", "jobs" to "id2"), prefs.driveFileIds.first())
    }

    @Test fun retention_roundTrips() = runTest {
        prefs.setRetention(Retention.DAYS30)
        assertEquals(Retention.DAYS30, prefs.retention.first())
    }

    @Test fun googleEmail_canBeCleared() = runTest {
        prefs.setGoogleEmail("a@b.com")
        assertEquals("a@b.com", prefs.googleEmail.first())
        prefs.setGoogleEmail(null)
        assertEquals(null, prefs.googleEmail.first())
    }
}
