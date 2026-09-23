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

/**
 * The single MobileWalletAdapter for the app. Pinned to mainnet at construction (the default is devnet).
 *
 * Read out of the clientlib 2.0.3 bytecode (MobileWalletAdapter$transact$2), because both facts are load-bearing
 * and neither is obvious from the API: transact() reads `blockchain` and `authToken`, then calls `authorize(...)`
 * with that cluster when the token is null, or `reauthorize(...)` when it is set, and passes the resulting
 * AuthorizationResult into the block. That is why the services take the block's parameter instead of calling
 * authorize() themselves (which always ran the full Connect flow), and why they do not pass a chain argument.
 */
object WalletSession {
    val adapter: MobileWalletAdapter? by lazy {
        try {
            MobileWalletAdapter(
                connectionIdentity = ConnectionIdentity(
                    identityUri = Uri.parse(AppConfig.Identity.URI),
                    iconUri = Uri.parse(AppConfig.Identity.ICON_PATH),
                    identityName = AppConfig.Identity.NAME,
                ),
            ).apply {
                blockchain = Solana.Mainnet
                // Restore the saved token so the wallet reauthorizes silently instead of showing its "Connect"
                // sheet before every single action. Without this the adapter starts with no token every launch.
                authToken = com.undersigned.app.data.UserPreferences.authToken
            }
        } catch (_: Exception) { null }
    }

    /**
     * Persist whatever token the adapter is holding after a wallet round-trip. The adapter updates its own
     * `authToken` on each authorize/reauthorize; unless it is written back, the next process start authorizes
     * from scratch. Call this after every transact, successful or not — a reauthorize can rotate the token.
     */
    fun rememberAuthToken() {
        val t = adapter?.authToken ?: return
        if (t != com.undersigned.app.data.UserPreferences.authToken) com.undersigned.app.data.UserPreferences.authToken = t
    }

    /** Sign In With Solana: proves control of the wallet and signs the consent statement. Moves no funds. */
    suspend fun signIn(sender: ActivityResultSender, statement: String): Result<SignInOutcome> {
        val mwa = adapter ?: return Result.failure(Exception("Wallet adapter unavailable on this device"))
        return try {
            val payload = SignInWithSolana.Payload(AppConfig.Identity.HOST, statement)
            val r = mwa.signIn(sender, payload)
            rememberAuthToken()  // sign-in is the first authorize; keep its token or the next action re-Connects
            when (r) {
                is TransactionResult.Success -> {
                    val p = r.payload
                    Result.success(SignInOutcome(Base58.encode(p.publicKey), Base58.encode(p.signature), String(p.signedMessage, Charsets.UTF_8)))
                }
                is TransactionResult.NoWalletFound -> Result.failure(Exception("No Solana wallet found on this device"))
                is TransactionResult.Failure -> Result.failure(Exception(WalletErrors.humanise(r.e, "Sign-in didn't complete. Try again.")))
            }
        } catch (e: Exception) { Result.failure(Exception(WalletErrors.humanise(e, "Sign-in didn't complete. Try again."))) }
    }
}
