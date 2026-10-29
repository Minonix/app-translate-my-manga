package com.tmm.mobile.transport

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tmm.mobile.data.AppPrefs
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

// Chiffrement du jeton d'appairage. Interface separee pour que les tests JVM (Robolectric n'a pas
// de vrai Keystore) injectent un chiffreur factice ; en production, c'est toujours KeystoreTokenCipher.
interface TokenCipher {
    fun encrypt(plain: String): String
    fun decrypt(encoded: String): String?
}

// AES-GCM 256 dont la cle ne quitte jamais l'Android Keystore (API plateforme, aucune dependance).
// Format stocke : base64(iv de 12 octets + texte chiffre).
class KeystoreTokenCipher : TokenCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)
    }

    // null si la cle a disparu (restauration sur un autre appareil, reinitialisation du Keystore) :
    // l'appelant traite le jeton comme absent et redemande un appairage.
    override fun decrypt(encoded: String): String? = try {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES)) }
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "tmm_direct_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

// Jeton d'appairage d'un emplacement (Hub ou PC) : seule la forme chiffree touche le DataStore, et il
// n'est jamais logge. La cle Keystore est commune aux deux emplacements (IV aleatoire a chaque chiffrement).
class DirectTokenStore(val slot: AppPrefs.DirectSlot, private val cipher: TokenCipher) {

    // Emplacement Hub par defaut (appels historiques et tests).
    constructor(prefs: AppPrefs, cipher: TokenCipher) : this(prefs.hubSlot, cipher)

    suspend fun get(): String? {
        val enc = slot.tokenEnc.first() ?: return null
        val plain = cipher.decrypt(enc)
        if (plain == null) slot.setTokenEnc(null)
        return plain
    }

    suspend fun set(token: String) {
        slot.setTokenEnc(cipher.encrypt(token))
        slot.setPairingExpired(false)
    }

    suspend fun clear() = slot.setTokenEnc(null)
}
