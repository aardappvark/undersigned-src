package com.undersigned.app.solana

import android.content.Context
import android.util.Log
import com.undersigned.app.AppConfig
import com.undersigned.app.util.Base58
import com.undersigned.app.util.GeoBlocker
import com.undersigned.app.util.WalletScreening
import com.undersigned.app.wallet.WalletErrors
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.delay

/**
 * Takes the one Pro payment, in SOL or SKR, the fleet way: region and sanctions gates, a funds precheck that
 * fails OPEN on RPC error, a mainnet simulation BEFORE the signature sheet opens, then inside transact{}
 * (which authorizes on mainnet): the same-account check, a fresh blockhash, sign and send. Nothing is reported as
 * paid until the cluster confirms it; a timeout says so and tells the user not to pay twice.
 *
 * Every failure keeps the money question in the first line: whether anything left the wallet.
 */
class ProPaymentService(private val rpc: SolanaRpcClient = SolanaRpcClient()) {

    enum class Rail { SOL, SKR }
    data class Receipt(val signature: String, val rail: Rail, val paidLamports: Long, val paidSkr: Long)
    /** The wallet sent it, the cluster has not confirmed it yet. Not a failure and not a success: read the ledger again in a moment. */
    class Unconfirmed(val signature: String) : Exception("Your payment was sent but is not confirmed yet. Nothing more will be charged. Do not pay again — re-read the ledger in a moment.")
    private class WouldFail(val reason: String) : Exception(reason)

    /** [sol]/[skr] default to the Pro price; a paid listing passes its own price and its `trs` [record] (see AppConfig.Payment.LIST_SOL). */
    suspend fun pay(context: Context, adapter: MobileWalletAdapter, sender: ActivityResultSender, signerAddress: String, rail: Rail, onStage: (ReviewTxService.Stage) -> Unit,
                    sol: Double = AppConfig.Payment.PRO_SOL, skr: Double = AppConfig.Payment.PRO_SKR, record: String? = null,
                    tipTo: String? = null, tipMemos: List<String> = emptyList()): Result<Receipt> {
        val signer = Base58.decode(signerAddress); require(signer.size == 32)
        // A tip ([tipTo] set) goes to the developer's proven wallet with [tipMemos] instead of the treasury and the fleet code.
        val to = tipTo ?: AppConfig.Payment.TREASURY
        val toKey = Base58.decode(to); require(toKey.size == 32)
        if (tipTo != null && WalletScreening.isSanctioned(tipTo)) return Result.failure(Exception("Nothing was sent. The developer's wallet appears on a sanctions list, so this app cannot take tips here."))
        if (runCatching { GeoBlocker(context).isBlocked() }.getOrDefault(false)) return Result.failure(Exception("Nothing was charged. Payment is not available in this region because of sanctions and legal restrictions."))
        if (WalletScreening.isSanctioned(signerAddress)) return Result.failure(Exception("Nothing was charged. This wallet address appears on a sanctions list and cannot be used with this app."))
        val lamports = AppConfig.Payment.lamports(sol)
        val skrRaw = AppConfig.Payment.skrRaw(skr)

        onStage(ReviewTxService.Stage.Simulating)
        // Funds precheck, failing OPEN: only a positive reading may stop a sale.
        when (rail) {
            Rail.SOL -> rpc.getBalance(signerAddress).getOrNull()?.let { have -> if (have < lamports + AppConfig.Payment.FEE_HEADROOM_LAMPORTS) return Result.failure(Exception("Nothing was charged. This wallet holds ${trim(have / 1e9)} SOL; the payment needs $sol SOL plus the network fee.")) }
            Rail.SKR -> rpc.getTokenBalanceRaw(signerAddress, AppConfig.Payment.SKR_MINT).getOrNull()?.let { have ->
                if (have < 0L) return Result.failure(Exception("Nothing was charged. This wallet holds no SKR token account yet. Pay in SOL, or hold some SKR first."))
                if (have < skrRaw) return Result.failure(Exception("Nothing was charged. This wallet holds ${String.format(java.util.Locale.US, "%,.2f", have / 1e6)} SKR; the payment needs ${String.format(java.util.Locale.US, "%,.0f SKR", skr)}."))
                rpc.getBalance(signerAddress).getOrNull()?.let { sol -> if (sol < AppConfig.Payment.FEE_HEADROOM_LAMPORTS) return Result.failure(Exception("Nothing was charged. The wallet needs a little SOL for the network fee (about ${trim(AppConfig.Payment.FEE_HEADROOM_LAMPORTS / 1e9)} SOL).")) }
            }
        }
        // Default to "ATA present" when the lookup FAILS: the treasury already holds an SKR account, and a wrong
        // "absent" would only add an idempotent create (harmless), while the wrong "present" costs nothing either.
        // For a tip the developer may never have held SKR: a failed lookup still says "present", but a real "absent" adds
        // the idempotent create, whose rent (about 0.002 SOL) the tipper's wallet pays; the tip sheet says so.
        val treasuryHasAta = if (rail == Rail.SKR) rpc.getTokenBalanceRaw(to, AppConfig.Payment.SKR_MINT).fold({ it >= 0L }, { true }) else true
        fun build(bh: ByteArray): ByteArray = when {
            tipTo != null && rail == Rail.SOL -> TxBuilder.tipSolTransaction(signer, toKey, lamports, bh, tipMemos)
            tipTo != null -> TxBuilder.tipSkrTransaction(signer, toKey, skrRaw, bh, createAta = !treasuryHasAta, memos = tipMemos)
            rail == Rail.SOL -> TxBuilder.proSolTransaction(signer, lamports, bh, record)
            else -> TxBuilder.proSkrTransaction(signer, skrRaw, bh, createTreasuryAta = !treasuryHasAta, record = record)
        }
        if (build(TxBuilder.ZERO_BLOCKHASH).size > AppConfig.Solana.MAX_TX_BYTES) return Result.failure(Exception("Nothing was sent. The review is too long to travel with a tip; shorten the note, or send the tip on its own."))
        // Gate: never open the signature sheet on a payment the cluster would reject.
        val sim = rpc.simulateTransaction(build(TxBuilder.ZERO_BLOCKHASH), replaceBlockhash = true)
            .getOrElse { return Result.failure(Exception("Nothing was charged. Could not reach Solana to check the payment: ${it.message?.take(80)}")) }
        if (sim.err != null) return Result.failure(Exception(explain(sim.err, sim.logs)))

        onStage(ReviewTxService.Stage.Signing)
        val result = try {
            adapter.transact(sender) { auth ->
                // transact has already authorized; the cluster is pinned by WalletSession's Solana.Mainnet. The
                // same-account gate below is unchanged — it is what keeps a payment on the signed-in wallet.
                val walletKey = auth.accounts.firstOrNull()?.publicKey ?: throw Exception("No account returned from wallet authorization")
                if (!walletKey.contentEquals(signer)) throw WouldFail("WrongWallet")
                val bh = rpc.getLatestBlockhash("confirmed").getOrThrow()
                val bhBytes = Base58.decode(bh.blockhash); require(bhBytes.size == 32)
                val r = signAndSendTransactions(transactions = arrayOf(build(bhBytes)))
                Base58.encode(r.signatures.first())
            }
        } catch (e: Exception) { Log.e(TAG, "pro payment failed", e); TransactionResult.Failure<String>(e.message ?: "The wallet did not complete the request.", e) }
        com.undersigned.app.wallet.WalletSession.rememberAuthToken()
        val signature = when (result) {
            is TransactionResult.Success -> result.payload
            is TransactionResult.NoWalletFound -> return Result.failure(Exception("Nothing was charged. No Solana wallet was found on this phone."))
            is TransactionResult.Failure -> return Result.failure(Exception(
                if ((result.e as? WouldFail)?.reason == "WrongWallet") "Nothing was charged. The wallet returned a different account from the one signed in. Switch accounts in your wallet and try again."
                else "Nothing was charged. " + WalletErrors.humanise(result.e, "The wallet did not complete the payment.")))
        }

        onStage(ReviewTxService.Stage.Confirming)
        val deadline = System.currentTimeMillis() + AppConfig.Payment.CONFIRM_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(2000)
            val st = rpc.getSignatureStatuses(listOf(signature)).getOrNull()?.firstOrNull() ?: continue
            if (st.err != null) return Result.failure(Exception("The payment did not go through on-chain, so nothing was charged: ${st.err.take(80)}"))
            if (st.confirmationStatus == "confirmed" || st.confirmationStatus == "finalized")
                return Result.success(Receipt(signature, rail, if (rail == Rail.SOL) lamports else 0L, if (rail == Rail.SKR) skrRaw else 0L))
        }
        Log.w(TAG, "payment $signature not confirmed within ${AppConfig.Payment.CONFIRM_TIMEOUT_MS} ms")
        return Result.failure(Unconfirmed(signature))
    }

    /** The raw simulation error belongs in the log, not on screen. */
    private fun explain(err: String, logs: List<String>): String {
        Log.w(TAG, "simulation refused: $err ${logs.takeLast(3)}")
        val all = err + logs.joinToString(" ")
        return "Nothing was charged. " + when {
            all.contains("insufficient lamports", true) || all.contains("InsufficientFunds", true) || all.contains("insufficient funds", true) -> "Not enough in your wallet to cover the payment and the network fee."
            all.contains("AccountNotFound", true) || all.contains("could not find account", true) -> "Your wallet does not hold this token yet."
            all.contains("BlockhashNotFound", true) -> "That took too long and the network moved on. Try again."
            else -> "The network rejected this payment before signing, so nothing was sent."
        }
    }

    private fun trim(v: Double): String = java.math.BigDecimal(v).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    companion object { private const val TAG = "ProPayment" }
}
