package com.tmm.mobile.data.status

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// PLAN_GEMINI.md 5.4 : table exacte des transitions autorisees. Un test par arete valide et par
// arete invalide notable (pas de produit cartesien complet, cf ChapterStatusTest pour le style).
class PhoneStateTransitionTest {

    @Test fun creation_onlyToPreparing() {
        assertTrue(isAllowedPhoneStateTransition(null, "PREPARING"))
        assertFalse(isAllowedPhoneStateTransition(null, "SENT"))
        assertFalse(isAllowedPhoneStateTransition(null, "FAILED"))
    }

    @Test fun preparing_allowsUploadingSentCancelFailed() {
        assertTrue(isAllowedPhoneStateTransition("PREPARING", "UPLOADING"))
        assertTrue(isAllowedPhoneStateTransition("PREPARING", "SENT"))
        assertTrue(isAllowedPhoneStateTransition("PREPARING", "CANCEL_REQUESTED"))
        assertTrue(isAllowedPhoneStateTransition("PREPARING", "FAILED"))
        assertFalse(isAllowedPhoneStateTransition("PREPARING", "RESULT_READY"))
        assertFalse(isAllowedPhoneStateTransition("PREPARING", "INSTALLED"))
    }

    @Test fun uploading_allowsSentCancelFailed_notInstalled() {
        assertTrue(isAllowedPhoneStateTransition("UPLOADING", "SENT"))
        assertTrue(isAllowedPhoneStateTransition("UPLOADING", "CANCEL_REQUESTED"))
        assertTrue(isAllowedPhoneStateTransition("UPLOADING", "FAILED"))
        assertFalse(isAllowedPhoneStateTransition("UPLOADING", "INSTALLED"))
        assertFalse(isAllowedPhoneStateTransition("UPLOADING", "DOWNLOADING"))
    }

    @Test fun sent_allowsResultReadyInstalledCancel_notFailedNotDownloading() {
        assertTrue(isAllowedPhoneStateTransition("SENT", "RESULT_READY"))
        assertTrue(isAllowedPhoneStateTransition("SENT", "INSTALLED"))
        assertTrue(isAllowedPhoneStateTransition("SENT", "CANCEL_REQUESTED"))
        assertFalse(isAllowedPhoneStateTransition("SENT", "FAILED"))
        assertFalse(isAllowedPhoneStateTransition("SENT", "DOWNLOADING"))
    }

    @Test fun resultReady_onlyToDownloading() {
        assertTrue(isAllowedPhoneStateTransition("RESULT_READY", "DOWNLOADING"))
        assertFalse(isAllowedPhoneStateTransition("RESULT_READY", "INSTALLED"))
        assertFalse(isAllowedPhoneStateTransition("RESULT_READY", "FAILED"))
    }

    @Test fun downloading_allowsInstalledOrFailed_notSent() {
        assertTrue(isAllowedPhoneStateTransition("DOWNLOADING", "INSTALLED"))
        assertTrue(isAllowedPhoneStateTransition("DOWNLOADING", "FAILED"))
        assertFalse(isAllowedPhoneStateTransition("DOWNLOADING", "SENT"))
    }

    @Test fun failed_canResumeReception_R10() {
        assertTrue(isAllowedPhoneStateTransition("FAILED", "RESULT_READY"))
        assertFalse(isAllowedPhoneStateTransition("FAILED", "PREPARING"))
    }

    @Test fun terminalStates_haveNoOutgoingTransition() {
        for (terminal in listOf("INSTALLED", "FAILED", "CANCEL_REQUESTED")) {
            for (candidate in listOf("PREPARING", "UPLOADING", "SENT", "RESULT_READY", "DOWNLOADING", "INSTALLED", "FAILED", "CANCEL_REQUESTED")) {
                // Seule exception : reprise R10 d'un echec de reception (FAILED -> RESULT_READY).
                if (terminal == "FAILED" && candidate == "RESULT_READY") continue
                assertFalse("$terminal -> $candidate ne devrait jamais etre autorise", isAllowedPhoneStateTransition(terminal, candidate))
            }
        }
    }
}
