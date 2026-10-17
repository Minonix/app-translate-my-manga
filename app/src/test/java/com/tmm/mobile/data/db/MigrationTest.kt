package com.tmm.mobile.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// docs/ERREURS_PROTOCOLE.md R14 : la base Room ne doit jamais empecher le demarrage. Base v2 construite
// depuis le schema exporte (app/schemas/.../2.json), ouverte par TmmDatabase.build() (le vrai builder) :
// la migration 2 -> 3 doit passer la validation de schema de Room et garder les lignes existantes.
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbFile: File get() = context.getDatabasePath("tmm.db")

    @Before
    fun setUp() {
        context.deleteDatabase("tmm.db")
    }

    @After
    fun tearDown() {
        context.deleteDatabase("tmm.db")
    }

    private fun schemaFile(version: Int): File =
        listOf("schemas", "app/schemas")
            .map { File(it, "com.tmm.mobile.data.db.TmmDatabase/$version.json") }
            .first { it.exists() }

    private fun createFromSchema(version: Int, seed: (SQLiteDatabase, List<kotlinx.serialization.json.JsonObject>) -> Unit) {
        val database = Json.parseToJsonElement(schemaFile(version).readText()).jsonObject.getValue("database").jsonObject
        val entities = database.getValue("entities").jsonArray.map { it.jsonObject }
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            for (entity in entities) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach { index ->
                    db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            database.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            seed(db, entities)
            db.version = version
        } finally {
            db.close()
        }
    }

    // Ligne job v2 : colonnes NOT NULL remplies selon leur affinite, les autres a NULL.
    private fun insertV2Job(db: SQLiteDatabase, entities: List<kotlinx.serialization.json.JsonObject>) {
        val fields = entities.single { it.getValue("tableName").jsonPrimitive.content == "job" }.getValue("fields").jsonArray.map { it.jsonObject }
        val columns = mutableListOf<String>()
        val values = mutableListOf<Any?>()
        for (field in fields) {
            val column = field.getValue("columnName").jsonPrimitive.content
            val notNull = field["notNull"]?.jsonPrimitive?.boolean == true
            columns += column
            values += when {
                column == "jobId" -> "old-job"
                column == "phoneState" -> "SENT"
                column == "mihonPath" -> "MangaSource/Titre/Chapitre 1"
                !notNull -> null
                field.getValue("affinity").jsonPrimitive.content == "TEXT" -> "x"
                else -> 0
            }
        }
        db.execSQL(
            "INSERT INTO job (${columns.joinToString(",") { "`$it`" }}) VALUES (${columns.joinToString(",") { "?" }})",
            values.toTypedArray(),
        )
    }

    @Test fun migrate2To3_keepsJobs_andAddsDefaults() = runTest {
        createFromSchema(2) { db, entities -> insertV2Job(db, entities) }

        val room = TmmDatabase.build(context)
        try {
            val job = room.jobDao().get("old-job")!!
            assertEquals("SENT", job.phoneState)
            assertNull(job.errorCode)
            assertFalse(job.redeliver)
            assertEquals(1, room.jobDao().observeAll().first().size)
        } finally {
            room.close()
        }
    }

    // Lien manuel (bouton dossier) : la table manual_chapter_link apparait, les telechargements restent.
    @Test fun migrate3To4_addsManualChapterLinkTable() = runTest {
        createFromSchema(3) { _, _ -> }

        val room = TmmDatabase.build(context)
        try {
            assertTrue(room.manualChapterLinkDao().getAll().isEmpty())
            room.manualChapterLinkDao().upsert(ManualChapterLinkEntity("Src/Serie/Ch 1.cbz", "sk", null, 1f))
            assertEquals(1, room.manualChapterLinkDao().getAll().size)
        } finally {
            room.close()
        }
    }

    // Base d'une version plus recente (retour a un APK plus ancien) : recreee vide, sans crash.
    @Test fun downgrade_isDestructive_neverCrashes() = runTest {
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE futur (id INTEGER PRIMARY KEY)")
            db.version = 99
        }

        val room = TmmDatabase.build(context)
        try {
            assertTrue(room.jobDao().observeAll().first().isEmpty())
        } finally {
            room.close()
        }
    }
}
