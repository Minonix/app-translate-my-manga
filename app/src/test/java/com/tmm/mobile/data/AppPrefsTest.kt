package com.tmm.mobile.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, "test_prefs.preferences_pb") },
        )
        prefs = AppPrefs(dataStore)
    }

    @Test fun defaults_matchPlanDefaults() = runTest {
        assertEquals(false, prefs.onboardingDone.first())
        assertEquals(false, prefs.wifiOnly.first())
        assertEquals(Retention.NEVER, prefs.retention.first())
    }

    // Hub servi sur :11443 : l'ancienne URL par defaut sans port, deja enregistree, est relue avec le port.
    @Test fun hubSlot_legacyDefaultUrlWithoutPort_isReadWithPort11443() = runTest {
        prefs.setDirectUrl(LEGACY_DEFAULT_DIRECT_URL)
        assertEquals("https://v2202610443888545433.tail8ac2b2.ts.net:11443/api/remote/v1/", prefs.directUrl.first())
        assertEquals(DEFAULT_DIRECT_URL, prefs.hubSlot.url.first())

        prefs.setDirectUrl("https://autre.example/api/remote/v1/")
        assertEquals("https://autre.example/api/remote/v1/", prefs.directUrl.first())
        prefs.setDirectUrl(null)
    }

    // Emplacements Hub et PC independants : cles distinctes, l'ancien emplacement "direct_*" reste le Hub.
    @Test fun directSlots_areIndependent() = runTest {
        prefs.setDirectUrl("https://hub.example/api/remote/v1/")
        prefs.pcSlot.setUrl("https://pc.example/api/remote/v1/")
        prefs.pcSlot.setPairingExpired(true)

        assertEquals("https://hub.example/api/remote/v1/", prefs.hubSlot.url.first())
        assertEquals("https://pc.example/api/remote/v1/", prefs.pcSlot.url.first())
        assertEquals(false, prefs.directPairingExpired.first())
        prefs.pcSlot.setUrl(null)
        prefs.pcSlot.setPairingExpired(false)
        prefs.setDirectUrl(null)
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

    // Installations simultanees : 10 par defaut, bornees 1..20 a l'ecriture comme a la lecture.
    @Test fun maxConcurrentInstalls_defaultsTo10_andIsClampedOnWriteAndRead() = runTest {
        assertEquals(10, prefs.maxConcurrentInstalls.first())
        prefs.setMaxConcurrentInstalls(0)
        assertEquals(1, prefs.maxConcurrentInstalls.first())
        prefs.setMaxConcurrentInstalls(50)
        assertEquals(20, prefs.maxConcurrentInstalls.first())
        prefs.setMaxConcurrentInstalls(7)
        assertEquals(7, prefs.maxConcurrentInstalls.first())

        // Valeur hors bornes deja enregistree (ecrite sans passer par le setter) : relue bornee.
        dataStore.edit { it[intPreferencesKey("max_concurrent_installs")] = 99 }
        assertEquals(20, prefs.maxConcurrentInstalls.first())
        dataStore.edit { it[intPreferencesKey("max_concurrent_installs")] = -3 }
        assertEquals(1, prefs.maxConcurrentInstalls.first())
    }
}
