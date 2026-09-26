package com.tmm.mobile.mihon

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream

// Implementation production de MihonFs via DocumentsContract (prefere a DocumentFile pour la
// performance, cf. CAHIER_DES_CHARGES.md 8.1). treeUri est l'arbre SAF persiste choisi a l'onboarding.
class MihonTree(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : MihonFs {

    private fun docUri(docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    private fun childrenUri(dirDocId: String): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE,
    )

    private fun Cursor.toEntry(): MihonDocEntry {
        val mime = getString(2)
        return MihonDocEntry(
            docId = getString(0),
            name = getString(1),
            isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
            lastModified = getLong(3),
            size = getLong(4),
        )
    }

    override fun children(dirDocId: String): List<MihonDocEntry> {
        val entries = mutableListOf<MihonDocEntry>()
        resolver.query(childrenUri(dirDocId), projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) entries.add(cursor.toEntry())
        }
        return entries
    }

    override fun findChild(dirDocId: String, name: String): MihonDocEntry? =
        children(dirDocId).firstOrNull { it.name == name }

    override fun createDir(dirDocId: String, name: String): String {
        val uri = DocumentsContract.createDocument(
            resolver, docUri(dirDocId), DocumentsContract.Document.MIME_TYPE_DIR, name,
        ) ?: error("createDir a echoue pour $name")
        return DocumentsContract.getDocumentId(uri)
    }

    override fun createFile(dirDocId: String, name: String, mimeType: String): String {
        val uri = DocumentsContract.createDocument(resolver, docUri(dirDocId), mimeType, name)
            ?: error("createFile a echoue pour $name")
        return DocumentsContract.getDocumentId(uri)
    }

    override fun rename(docId: String, newName: String): String {
        // renameDocument retourne null quand le documentId n'a pas change (cas courant) :
        // l'ancien docId reste valide, on le garde dans ce cas.
        val newUri = DocumentsContract.renameDocument(resolver, docUri(docId), newName)
        return if (newUri != null) DocumentsContract.getDocumentId(newUri) else docId
    }

    override fun delete(docId: String) {
        DocumentsContract.deleteDocument(resolver, docUri(docId))
    }

    override fun openInput(docId: String): InputStream =
        resolver.openInputStream(docUri(docId)) ?: error("openInput a echoue pour $docId")

    override fun openOutput(docId: String): OutputStream =
        resolver.openOutputStream(docUri(docId)) ?: error("openOutput a echoue pour $docId")
}
