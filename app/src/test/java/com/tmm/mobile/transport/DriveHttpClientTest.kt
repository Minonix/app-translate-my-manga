package com.tmm.mobile.transport

import org.junit.Assert.assertTrue
import org.junit.Test

// Lien faible : les 10 s par defaut d'OkHttp coupaient l'attente de la reponse Google apres un morceau
// de 8 Mo. Le client Drive doit tolerer au moins une minute de silence en lecture et en ecriture.
class DriveHttpClientTest {
    @Test fun driveClient_toleratesSlowLinks() {
        val client = driveHttpClient()
        assertTrue(client.connectTimeoutMillis >= 20_000)
        assertTrue(client.readTimeoutMillis >= 60_000)
        assertTrue(client.writeTimeoutMillis >= 60_000)
    }
}
