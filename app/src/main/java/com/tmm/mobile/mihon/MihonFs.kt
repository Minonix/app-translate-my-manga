package com.tmm.mobile.mihon

import java.io.InputStream
import java.io.OutputStream

data class MihonDocEntry(
    val docId: String,
    val name: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val size: Long,
)

// Seule interface a deux implementations autorisee en dehors de JobTransport (cf. PLAN_GEMINI.md) :
// MihonTree (production, DocumentsContract sur l'arbre SAF choisi a l'onboarding) et FakeMihonFs (tests).
interface MihonFs {
    fun children(dirDocId: String): List<MihonDocEntry>
    fun findChild(dirDocId: String, name: String): MihonDocEntry?
    fun createDir(dirDocId: String, name: String): String
    fun createFile(dirDocId: String, name: String, mimeType: String): String
    fun rename(docId: String, newName: String): String
    fun delete(docId: String)
    fun openInput(docId: String): InputStream
    fun openOutput(docId: String): OutputStream
}

/** Arbre Mihon choisi a l'onboarding : acces fichiers + docId de sa racine. */
data class MihonRoot(val fs: MihonFs, val rootDocId: String)
