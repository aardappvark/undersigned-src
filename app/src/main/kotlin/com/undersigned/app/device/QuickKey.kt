package com.undersigned.app.device

import android.content.Context
import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/**
 * The phone's own key for quick reviews (owner, 10 Oct 2026): made on first use, kept in this app's private storage,
 * never leaves the phone. It signs only quick-review records; the relayer pays the fee. It is not a wallet: it holds
 * nothing and moves nothing. One key per install, so a phone's quick reviews read as one anonymous voice.
 */
object QuickKey {
    private const val PREFS = "undersigned_quick"; private const val SEED = "seed"

    @Synchronized private fun key(ctx: Context): Ed25519PrivateKeyParameters {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seed = p.getString(SEED, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: ByteArray(32).also {
            SecureRandom().nextBytes(it); p.edit().putString(SEED, Base64.encodeToString(it, Base64.NO_WRAP)).commit()
        }
        return Ed25519PrivateKeyParameters(seed, 0)
    }

    fun publicKey(ctx: Context): ByteArray = key(ctx).generatePublicKey().encoded

    fun sign(ctx: Context, message: ByteArray): ByteArray = Ed25519Signer().run { init(true, key(ctx)); update(message, 0, message.size); generateSignature() }
}
