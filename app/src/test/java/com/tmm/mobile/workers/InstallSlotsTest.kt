package com.tmm.mobile.workers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Limite d'installations simultanees reglable (InstallSlots) et delai d'inactivite (IdleWatch).
class InstallSlotsTest {

    @Test fun limitIncrease_releasesWaitingInstall_andSlotsAreFreedAfterwards() = runTest {
        val limit = MutableStateFlow(1)
        val gate = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        val jobs = listOf("a", "b").map { id ->
            launch { InstallSlots.withSlot(id, limit) { started += id; gate.await() } }
        }
        runCurrent()
        assertEquals(listOf("a"), started)
        assertTrue(InstallSlots.isWaiting("b"))
        assertEquals(1, InstallSlots.runningCount)

        limit.value = 2
        runCurrent()
        assertEquals(listOf("a", "b"), started)
        assertFalse(InstallSlots.isWaiting("b"))
        assertEquals(2, InstallSlots.runningCount)

        gate.complete(Unit)
        jobs.joinAll()
        assertEquals(0, InstallSlots.runningCount)
    }

    // Une baisse ne coupe pas les installations en cours ; elle s'applique aux suivantes.
    @Test fun limitDecrease_appliesToNextInstallsOnly() = runTest {
        val limit = MutableStateFlow(2)
        val gates = mapOf("a" to CompletableDeferred<Unit>(), "b" to CompletableDeferred<Unit>(), "c" to CompletableDeferred<Unit>())
        val started = mutableListOf<String>()
        val jobs = listOf("a", "b").map { id -> launch { InstallSlots.withSlot(id, limit) { started += id; gates.getValue(id).await() } } }
        runCurrent()
        limit.value = 1
        val third = launch { InstallSlots.withSlot("c", limit) { started += "c"; gates.getValue("c").await() } }
        runCurrent()
        assertEquals(listOf("a", "b"), started)
        assertTrue(InstallSlots.isWaiting("c"))

        gates.getValue("a").complete(Unit)
        runCurrent()
        // Encore une installation en cours (b) pour une limite de 1 : c attend toujours.
        assertEquals(listOf("a", "b"), started)
        gates.getValue("b").complete(Unit)
        runCurrent()
        assertEquals(listOf("a", "b", "c"), started)

        gates.getValue("c").complete(Unit)
        (jobs + third).joinAll()
        assertEquals(0, InstallSlots.runningCount)
    }

    // Transfert lent qui progresse (un bloc toutes les 9 min pendant 1 h) : jamais coupe.
    @Test fun idleWatch_slowButProgressingTransfer_isNeverIdle() {
        val clock = TestClock()
        val watch = IdleWatch(clock, INSTALL_IDLE_MINUTES * 60_000L)
        var received = 0L
        repeat(7) {
            clock.millis += 9 * 60_000L
            assertFalse(watch.isIdle())
            received += 64 * 1024
            watch.onProgress(received)
            assertFalse(watch.isIdle())
        }
    }

    // Aucun octet nouveau depuis INSTALL_IDLE_MINUTES : inactif, meme si un evenement repete le meme total.
    @Test fun idleWatch_noNewBytesFor10Minutes_isIdle() {
        val clock = TestClock()
        val watch = IdleWatch(clock, INSTALL_IDLE_MINUTES * 60_000L)
        watch.onProgress(1000)
        clock.millis += 9 * 60_000L
        watch.onProgress(1000)
        assertFalse(watch.isIdle())
        clock.millis += 60_000L
        assertTrue(watch.isIdle())
    }
}
