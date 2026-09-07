package com.undersigned.app.wallet

import android.net.Uri
import com.undersigned.app.AppConfig
import com.undersigned.app.util.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana

data class SignInOutcome(val address: String, val signature: String, val signedMessage: String)

/** The single MobileWalletAdapter for the app. Pinned to mainnet at construction (the default is devnet). */
object WalletSession {
    val adapter: MobileWalletAdapter? by lazy {
        try {
            MobileWalletAdapter(
                connectionIdentity = ConnectionIdentity(
                    identityUri = Uri.parse(AppConfig.Identity.URI),
                    iconUri = Uri.parse(AppConfig.Identity.ICON_PATH),
                    identityName = AppConfig.Identity.NAME,
                ),
            ).apply { blockchain = Solana.Mainnet }
        } catch (_: Exception) { null }
    }

    /** Sign In With Solana: proves control of the wallet and signs the consent statement. Moves no funds. */
    suspend fun signIn(sender: ActivityResultSender, statement: String): Result<SignInOutcome> {
        val mwa = adapter ?: return Result.failure(Exception("Wallet adapter unavailable on this device"))
        return try {
            val payload = SignInWithSolana.Payload(AppConfig.Identity.HOST, statement)
            when (val r = mwa.signIn(sender, payload)) {
                is TransactionResult.Success -> {
                    val p = r.payload
                    Result.success(SignInOutcome(Base58.encode(p.publicKey), Base58.encode(p.signature), String(p.signedMessage, Charsets.UTF_8)))
                }
                is TransactionResult.NoWalletFound -> Result.failure(Exception("No Solana wallet found on this device"))
                is TransactionResult.Failure -> Result.failure(Exception(r.e.message?.take(120) ?: "Sign-in failed"))
            }
        } catch (e: Exception) { Result.failure(Exception(e.message?.take(120) ?: "Sign-in failed")) }
    }
}
