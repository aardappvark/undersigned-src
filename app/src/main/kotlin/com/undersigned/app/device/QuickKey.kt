package com.undersigned.app.device

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The phone's own key for quick reviews (owner, 10 Oct 2026): made on first use, kept in this app's private storage,
 * never leaves the phone. It signs only quick-review records; the relayer pays the fee. It is not a wallet: it holds
 * nothing and moves nothing. One key per install, so a phone's quick reviews read as one anonymous voice.
 *
 * Storage (12 Oct 2026, security audit): the 32-byte seed is encrypted with AES-256-GCM under a key that lives in the
 * Android Keystore (hardware-backed where the device has it, never exportable); only the ciphertext and nonce sit in
 * SharedPreferences. A seed written by an earlier build in the clear is re-encrypted on first read and the plain copy
 * removed. Backups are off for the app (`allowBackup=false`), so the ciphertext never leaves the sandbox either.
 */
object QuickKey {
    private const val PREFS = "undersigned_quick"; private const val SEED = "seed"; private const val SEALED = "seed_sealed"; private const val NONCE = "seed_nonce"
    private const val ALIAS = "undersigned_quick_seal"

    private fun sealKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return gen.generateKey()
    }

    private fun seal(seed: ByteArray): Pair<String, String> {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, sealKey()) }
        return Base64.encodeToString(c.doFinal(seed), Base64.NO_WRAP) to Base64.encodeToString(c.iv, Base64.NO_WRAP)
    }

    private fun open(sealed: String, nonce: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, sealKey(), GCMParameterSpec(128, Base64.decode(nonce, Base64.NO_WRAP))) }
        return c.doFinal(Base64.decode(sealed, Base64.NO_WRAP))
    }

    @Synchronized private fun key(ctx: Context): Ed25519PrivateKeyParameters {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sealed = p.getString(SEALED, null); val nonce = p.getString(NONCE, null)
        val seed: ByteArray = if (sealed != null && nonce != null) {
            runCatching { open(sealed, nonce) }.getOrElse { fresh(p) }   // a keystore reset (device wipe, key invalidated) means a new anonymous voice
        } else {
            val legacy = p.getString(SEED, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
            if (legacy != null && legacy.size == 32) { store(p, legacy); legacy } else fresh(p)
        }
        return Ed25519PrivateKeyParameters(seed, 0)
    }

    private fun fresh(p: android.content.SharedPreferences): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it); store(p, it) }

    private fun store(p: android.content.SharedPreferences, seed: ByteArray) {
        val (sealed, nonce) = seal(seed)
        p.edit().putString(SEALED, sealed).putString(NONCE, nonce).remove(SEED).commit()
    }

    fun publicKey(ctx: Context): ByteArray = key(ctx).generatePublicKey().encoded

    fun sign(ctx: Context, message: ByteArray): ByteArray = Ed25519Signer().run { init(true, key(ctx)); update(message, 0, message.size); generateSignature() }
}
