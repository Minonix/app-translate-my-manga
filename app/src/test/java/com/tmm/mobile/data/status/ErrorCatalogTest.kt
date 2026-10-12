package com.tmm.mobile.data.status

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tmm.mobile.R
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.protocol.ProtocolJson
import com.tmm.mobile.protocol.StatusJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// docs/ERREURS_PROTOCOLE.md §1 : le catalogue central code -> libelle/action couvre exactement
// error_codes.json (fixture partagee avec TMM-Win), et chaque code PC publie dans un status.json
// "failed" se decode et s'affiche avec son propre libelle.
@RunWith(RobolectricTestRunner::class)
class ErrorCatalogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val fixture: JsonObject by lazy {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream("protocol/error_codes.json")) { "fixture manquante" }
            .bufferedReader().use { it.readText() }
        ProtocolJson.parseToJsonElement(text).jsonObject
    }

    private fun entries(key: String) = fixture.getValue(key).jsonArray.map { it.jsonObject }
    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun action(name: String) = ErrorAction.valueOf(name.uppercase())

    private fun assertDisplayable(label: Int) {
        val text = context.getString(label)
        assertTrue(text.isNotBlank())
        assertNotEquals(context.getString(R.string.error_unknown), text)
    }

    @Test fun pcJobCodes_matchFixture_withLabelAndAction() {
        val expected = entries("pcJob").associate { it.str("code") to action(it.str("action")) }
        assertEquals(expected.keys, ErrorCatalog.pcJob.keys)
        for ((code, act) in expected) {
            assertEquals(code, act, ErrorCatalog.pcJob.getValue(code).action)
            assertDisplayable(ErrorCatalog.pcJob.getValue(code).label)
        }
    }

    @Test fun phoneLocalCodes_matchFixture_withLabelAndAction() {
        val expected = entries("phoneLocal").associate { it.str("code") to action(it.str("action")) }
        assertEquals(expected.keys, ErrorCatalog.phoneLocal.keys)
        for ((code, act) in expected) {
            assertEquals(code, act, ErrorCatalog.phoneLocal.getValue(code).action)
            assertDisplayable(ErrorCatalog.phoneLocal.getValue(code).label)
        }
    }

    @Test fun settingsRejectCodes_matchFixture_withLabel() {
        val expected = fixture.getValue("settingsReject").jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(expected, ErrorCatalog.settingsReject.keys)
        expected.forEach { assertDisplayable(ErrorCatalog.settingsRejectLabel(it)) }
    }

    // Un status.json "failed" par code PC : decode, applique a un job SENT comme le fait RefreshCoordinator,
    // puis resolu par forJob (ce que lit l'ecran Activite).
    @Test fun samplePcFailedStatus_parsesAndDisplays_forEveryCode() {
        for (entry in entries("pcJob")) {
            val code = entry.str("code")
            val raw = """{"protocol":1,"jobId":"j","updatedAt":"2026-01-01T00:00:00Z","mode":"translate","state":"failed",""" +
                """"stage":null,"page":null,"pages":null,"engine":null,"queuePosition":null,"error":{"code":"$code","message":"m","page":3}}"""
            val status = ProtocolJson.decodeFromString(StatusJson.serializer(), raw)
            val info = ErrorCatalog.forJob(job(pcState = status.state, pcErrorCode = status.error?.code))
            assertEquals(code, info.code)
            assertEquals(ErrorCatalog.pcJob.getValue(code).label, info.label)
        }
    }

    // Code inconnu (PC plus recent) : libelle generique + Reessayer, jamais de crash.
    @Test fun unknownCode_genericLabel_andRetry() {
        val info = ErrorCatalog.forJob(job(pcState = "failed", pcErrorCode = "code_futur"))
        assertEquals(R.string.error_unknown, info.label)
        assertEquals(ErrorAction.RETRY, info.action)
        assertEquals(ErrorAction.RETRY, ErrorCatalog.forLocal(null).action)
    }

    private fun job(pcState: String?, pcErrorCode: String?) = JobEntity(
        jobId = "j", mode = "translate", pages = null, translate = true,
        mihonPath = "S/T/C", sourceDir = "S", seriesDir = "T", chapterDir = "C",
        seriesKey = null, chapterKey = null, phoneState = "SENT",
        failedStep = null, errorMessage = null, driveFolderId = null, uploadSessionUrl = null,
        cachePath = null, sha256 = null, sizeBytes = null, transferredBytes = 0, transferTotal = 0,
        pcState = pcState, pcStage = null, pcPage = null, pcPages = null, pcEngine = null,
        pcQueuePosition = null, pcErrorCode = pcErrorCode, pcErrorMessage = null, pcErrorPage = null,
        statusUpdatedAt = null, resultJson = null, resultFileId = null,
        createdAt = 0, updatedAt = 0, finishedAt = null, installedAt = null, installedPath = null,
        receivedWrittenAt = null, cancelWrittenAt = null, driveDeletedAt = null,
    )
}
