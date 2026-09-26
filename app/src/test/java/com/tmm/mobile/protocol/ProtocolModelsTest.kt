package com.tmm.mobile.protocol

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// CdC 7.2/7.5 : chaque fixture est copiee telle quelle depuis le cahier des charges
// (app/src/test/resources/protocol/*.json), pour verifier que nos modeles decodent
// exactement les schemas contractuels, pas une version reconstruite de memoire.
class ProtocolModelsTest {

    private fun fixture(name: String) =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("protocol/$name")) { "fixture manquante : $name" }
            .bufferedReader().readText()

    @Test
    fun requestJson_decodesCdcExample() {
        val decoded = ProtocolJson.decodeFromString(RequestJson.serializer(), fixture("request.json"))
        assertEquals("7f1c...", decoded.jobId)
        assertEquals("Blue Lock", decoded.series.title)
        assertEquals(214.0, decoded.chapter.number, 0.0)
        assertEquals("Team_Chapter 214", decoded.mihonPath.chapter)
        assertEquals(18234567L, decoded.file?.size)
        assertEquals("translate", decoded.options.mode)
        assertEquals(null, decoded.options.pages)
        roundTrip(RequestJson.serializer(), decoded)
    }

    @Test
    fun statusJson_decodesCdcExample() {
        val decoded = ProtocolJson.decodeFromString(StatusJson.serializer(), fixture("status.json"))
        assertEquals("translating", decoded.state)
        assertEquals("lettering", decoded.stage)
        assertEquals(14, decoded.page)
        assertEquals(null, decoded.error)
        roundTrip(StatusJson.serializer(), decoded)
    }

    @Test
    fun resultJson_decodesCdcExample() {
        val decoded = ProtocolJson.decodeFromString(ResultJson.serializer(), fixture("result.json"))
        assertEquals("Blue Lock", decoded.localFolderName)
        assertEquals(22, decoded.pages)
        val quality = checkNotNull(decoded.quality)
        assertEquals(146, quality.translatedBubbles)
        assertEquals(1, quality.alerts.size)
        assertEquals("overflow", quality.alerts.first().kind)
        assertEquals(8, quality.alerts.first().page)
        assertTrue(quality.alerts.first().repairable)
        roundTrip(ResultJson.serializer(), decoded)
    }

    @Test
    fun cancelJson_decodesCdcExample() {
        val decoded = ProtocolJson.decodeFromString(CancelJson.serializer(), fixture("cancel.json"))
        assertEquals("7f1c...", decoded.jobId)
        roundTrip(CancelJson.serializer(), decoded)
    }

    @Test
    fun receivedJson_decodesCdcExample() {
        val decoded = ProtocolJson.decodeFromString(ReceivedJson.serializer(), fixture("received.json"))
        assertEquals("local/Blue Lock/Chapter 214.cbz", decoded.installedPath)
        roundTrip(ReceivedJson.serializer(), decoded)
    }

    @Test
    fun heartbeat_decodesCdcExample() {
        val decoded = ProtocolJson.decodeFromString(Heartbeat.serializer(), fixture("heartbeat.json"))
        assertEquals("PC-BUREAU", decoded.pcName)
        assertEquals(4, decoded.slots)
        assertEquals(listOf("7f1c...", "..."), decoded.activeJobIds)
        assertEquals(4, decoded.queue.active)
        roundTrip(Heartbeat.serializer(), decoded)
    }

    @Test
    fun pcIndex_decodesCdcExample() {
        val decoded = ProtocolJson.decodeFromString(PcIndex.serializer(), fixture("index.json"))
        assertEquals(1, decoded.chapters.size)
        assertTrue(decoded.chapters.first().original)
        assertTrue(decoded.chapters.first().fr)
        roundTrip(PcIndex.serializer(), decoded)
    }

    @Test
    fun pcSettings_decodesCdcExample() {
        val decoded = ProtocolJson.decodeFromString(PcSettings.serializer(), fixture("settings.json"))
        assertEquals(12, decoded.version)
        assertEquals("c0a1...", decoded.lastCommandId)
        assertEquals("gemini", decoded.values.translationProvider)
        assertEquals(4, decoded.values.chapterConcurrency)
        assertEquals(1280, decoded.values.inpaintingSize)
        roundTrip(PcSettings.serializer(), decoded)
    }

    @Test
    fun settingsCommand_decodesCdcExample() {
        val decoded = ProtocolJson.decodeFromString(SettingsCommand.serializer(), fixture("command.json"))
        assertEquals("c0a1...", decoded.cmdId)
        assertEquals(12, decoded.baseVersion)
        assertEquals(JsonPrimitive(2), decoded.changes["chapterConcurrency"])
        roundTrip(SettingsCommand.serializer(), decoded)
    }

    @Test
    fun settingsSchema_rejectsValueOutsideClosedList() {
        assertTrue(SettingsSchema.isAllowed("targetLanguage", "FRA"))
        assertFalse(SettingsSchema.isAllowed("targetLanguage", "ITA"))
        assertTrue(SettingsSchema.isAllowed("chapterConcurrency", "4"))
        assertFalse(SettingsSchema.isAllowed("chapterConcurrency", "5"))
        assertFalse(SettingsSchema.isAllowed("uneCleInconnue", "quoiquecesoit"))
    }

    private fun <T> roundTrip(serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        val reencoded = ProtocolJson.encodeToString(serializer, value)
        val redecoded = ProtocolJson.decodeFromString(serializer, reencoded)
        assertEquals(value, redecoded)
    }
}
