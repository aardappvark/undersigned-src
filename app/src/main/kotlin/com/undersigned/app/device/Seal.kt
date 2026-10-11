package com.undersigned.app.device

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small secrets at rest (audit, 12 Oct 2026): AES-256-GCM under a key that lives in the Android Keystore (hardware-backed
 * where the device has it, never exportable). What is stored is `nonce.ciphertext` in base64; a value that no longer opens
 * (a keystore reset after a device wipe) reads as absent. Used for the quick-review seed and the wallet's auth token.
 */
object Seal {
    private const val ALIAS = "undersigned_seal"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return gen.generateKey()
    }

    fun seal(plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(c.doFinal(plain), Base64.NO_WRAP)
    }

    fun open(sealed: String): ByteArray? = runCatching {
        val (iv, ct) = sealed.split('.', limit = 2)
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))) }.doFinal(Base64.decode(ct, Base64.NO_WRAP))
    }.getOrNull()

    fun sealText(s: String): String = seal(s.toByteArray(Charsets.UTF_8))
    fun openText(sealed: String): String? = open(sealed)?.toString(Charsets.UTF_8)
}
