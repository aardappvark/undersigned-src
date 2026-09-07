package com.undersigned.app.solana

import android.net.Uri
import android.util.Log
import com.undersigned.app.AppConfig
import com.undersigned.app.util.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.delay

/**
 * Publishes one SRL1 record. Order of operations is the fleet's money-code gate even though no money
 * moves: simulate first (sigVerify off, blockhash replaced), then inside transact{}: authorize on
 * mainnet, check the wallet returned the SAME account the app is signed in as, fetch a "confirmed"
 * blockhash AFTER authorize, rebuild, signAndSendTransactions. Then poll for confirmation.
 */
class ReviewTxService(private val rpc: SolanaRpcClient = SolanaRpcClient()) {

    sealed class Stage { object Simulating : Stage(); object Signing : Stage(); object Confirming : Stage() }

    suspend fun publish(adapter: MobileWalletAdapter, sender: ActivityResultSender, signerAddress: String, memoText: String, onStage: (Stage) -> Unit): Result<String> {
        val signer = Base58.decode(signerAddress); require(signer.size == 32)
        onStage(Stage.Simulating)
        val sim = rpc.simulateTransaction(TxBuilder.reviewTransaction(signer, memoText, TxBuilder.ZERO_BLOCKHASH), replaceBlockhash = true)
            .getOrElse { return Result.failure(Exception("Could not reach Solana to check the transaction: ${it.message?.take(80)}")) }
        if (sim.err != null) return Result.failure(Exception("The cluster rejected the transaction: ${sim.err.take(120)}"))

        onStage(Stage.Signing)
        val result = try {
            adapter.transact(sender) {
                val auth = authorize(identityUri = Uri.parse(AppConfig.Identity.URI), iconUri = Uri.parse(AppConfig.Identity.ICON_PATH), identityName = AppConfig.Identity.NAME, chain = "solana:mainnet")
                val walletKey = auth.accounts.firstOrNull()?.publicKey ?: throw Exception("No account returned from wallet authorization")
                if (!walletKey.contentEquals(signer)) throw Exception("The wallet returned a different account (${shortKey(Base58.encode(walletKey))}) from the one signed in. Switch accounts in your wallet and try again.")
                val bh = rpc.getLatestBlockhash("confirmed").getOrThrow()
                val bhBytes = Base58.decode(bh.blockhash); require(bhBytes.size == 32)
                val tx = TxBuilder.reviewTransaction(signer, memoText, bhBytes)
                val r = signAndSendTransactions(transactions = arrayOf(tx))
                Base58.encode(r.signatures.first())
            }
        } catch (e: Exception) {
            Log.e(TAG, "publish failed", e)
            TransactionResult.Failure<String>(e.message ?: "The wallet did not complete the request.", e)
        }
        val signature = when (result) {
            is TransactionResult.Success -> result.payload
            is TransactionResult.NoWalletFound -> return Result.failure(Exception("No Solana wallet found. Install a wallet that supports Mobile Wallet Adapter."))
            is TransactionResult.Failure -> return Result.failure(Exception(result.e.message ?: "The wallet declined the request."))
        }

        onStage(Stage.Confirming)
        val deadline = System.currentTimeMillis() + AppConfig.Ledger.CONFIRM_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(2500)
            val st = rpc.getSignatureStatuses(listOf(signature)).getOrNull()?.firstOrNull() ?: continue
            if (st.err != null) return Result.failure(Exception("Transaction failed on-chain: ${st.err.take(100)}"))
            if (st.confirmationStatus == "confirmed" || st.confirmationStatus == "finalized") return Result.success(signature)
        }
        // Not seen yet: the signature is real; the ledger reader will pick it up. Report it as sent.
        return Result.success(signature)
    }

    companion object { private const val TAG = "ReviewTx" }
}
