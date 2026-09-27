package com.tmm.mobile.ui.screens.permission

import android.net.Uri
import com.tmm.mobile.mihon.FakeMihonFs
import com.tmm.mobile.workers.WorkerTestEnvironment
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// PLAN_GEMINI.md 7.13 : verifie la seule regle bloquante (downloads/ present) sur FakeMihonFs, sans
// SAF ni Activity (le lancement d'OpenDocumentTree/takePersistableUriPermission vit dans PermissionRoute).
@RunWith(RobolectricTestRunner::class)
class PermissionViewModelTest {
    private val env = WorkerTestEnvironment()
    private lateinit var root: File

    @Before
    fun setUp() {
        env.setUp()
        root = kotlin.io.path.createTempDirectory().toFile()
    }

    @After
    fun tearDown() {
        env.tearDown()
        root.deleteRecursively()
    }

    private fun vm() = PermissionViewModel(env.container)

    @Test
    fun verify_withDownloadsFolder_marksFixedAndPersistsUri() = runTest {
        File(root, "downloads").mkdirs()
        val fs = FakeMihonFs(root)
        val treeUri = Uri.parse("content://fake/tree/${root.absolutePath}")
        val vm = vm()

        vm.verify(fs, root.absolutePath, treeUri)

        val state = vm.state.value
        assertTrue(state.isFixed)
        assertFalse(state.isBusy)
        assertNull(state.errorMessage)
        assertEquals(treeUri.toString(), env.prefs.mihonTreeUri.first())
    }

    @Test
    fun verify_withoutDownloadsFolder_showsErrorAndDoesNotPersist() = runTest {
        val fs = FakeMihonFs(root)
        val treeUri = Uri.parse("content://fake/tree/${root.absolutePath}")
        val vm = vm()

        vm.verify(fs, root.absolutePath, treeUri)

        val state = vm.state.value
        assertFalse(state.isFixed)
        assertFalse(state.isBusy)
        assertEquals(PermissionViewModel.MISSING_DOWNLOADS, state.errorMessage)
        assertNull(env.prefs.mihonTreeUri.first())
    }
}
