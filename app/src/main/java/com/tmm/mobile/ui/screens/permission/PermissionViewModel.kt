package com.tmm.mobile.ui.screens.permission

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tmm.mobile.Startup
import com.tmm.mobile.StartupResult
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.mihon.MihonTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// PLAN_GEMINI.md 7.13 : ecran "Accès Mihon perdu". Seule downloads/ est bloquante (comme a l'onboarding,
// E6.7) - local/ et autobackup/ sont utilises s'ils existent mais leur absence ne bloque pas la reprise.
class PermissionViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(PermissionState())
    val state: StateFlow<PermissionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val uriString = container.prefs.mihonTreeUri.first()
            val path = uriString?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':')?.let { seg -> "/$seg" } }
            if (path != null) _state.update { it.copy(expectedPath = path) }
        }
    }

    fun onEvent(event: PermissionEvent) {
        when (event) {
            PermissionEvent.PickFolder -> {} // la Route lance OpenDocumentTree puis appelle onFolderPicked
        }
    }

    fun onFolderPicked(uri: Uri?) {
        if (uri == null) return
        _state.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            container.context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val tree = MihonTree(container.context.contentResolver, uri)
            val rootId = DocumentsContract.getTreeDocumentId(uri)
            verify(tree, rootId, uri)
        }
    }

    internal suspend fun verify(mihonFs: MihonFs, rootDocId: String, treeUri: Uri) {
        val hasDownloads = mihonFs.findChild(rootDocId, "downloads") != null
        if (!hasDownloads) {
            _state.update { it.copy(isBusy = false, isFixed = false, errorMessage = MISSING_DOWNLOADS) }
            return
        }
        container.prefs.setMihonTreeUri(treeUri.toString())
        val displayPath = treeUri.lastPathSegment?.substringAfterLast(':')?.let { "/$it" } ?: treeUri.toString()
        _state.update { it.copy(isBusy = false, isFixed = true, errorMessage = null, expectedPath = displayPath) }
    }

    suspend fun runStartupAndFinish(): StartupResult = withContext(Dispatchers.IO) { container.startup.run() }

    companion object {
        const val MISSING_DOWNLOADS = "MISSING_DOWNLOADS"
    }
}
