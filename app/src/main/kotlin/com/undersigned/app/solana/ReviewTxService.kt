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
 * moves: simulate first (sigVerify off, blockhash replaced), then inside transact{} — which authorizes on
 * mainnet for us — check the wallet returned the SAME account the app is signed in as, fetch a "confirmed"
 * blockhash AFTER that, rebuild, signAndSendTransactions. Then poll for confirmation.
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
            adapter.transact(sender) { auth ->
                // Use the authorization transact already performed. Calling authorize() again here forced a FULL
                // authorize on every action, so the wallet showed its "Connect" sheet before every flag, vote and
                // reply (measured on device, 21 Sep 2026). The adapter reauthorizes silently from its saved token.
                // The cluster is pinned by WalletSession's `blockchain = Solana.Mainnet`, not by this call.
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
        com.undersigned.app.wallet.WalletSession.rememberAuthToken()
        val signature = when (result) {
            is TransactionResult.Success -> result.payload
            is TransactionResult.NoWalletFound -> return Result.failure(Exception("No Solana wallet found. Install a wallet that supports Mobile Wallet Adapter."))
            is TransactionResult.Failure -> return Result.failure(Exception(com.undersigned.app.wallet.WalletErrors.humanise(result.e)))
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

    /**
     * Publish many records with ONE wallet approval: records are packed into as few transactions as
     * fit, every transaction is simulated first, then signAndSendTransactions takes the whole set.
     * Returns the transaction signatures in order (records map to them through chunk order).
     */
    suspend fun publishBatch(adapter: MobileWalletAdapter, sender: ActivityResultSender, signerAddress: String, memoTexts: List<String>, onStage: (Stage) -> Unit): Result<List<Pair<String, List<String>>>> {
        val signer = Base58.decode(signerAddress); require(signer.size == 32)
        val chunks = TxBuilder.chunkForBatches(signer, memoTexts)
        onStage(Stage.Simulating)
        for (c in chunks) {
            val sim = rpc.simulateTransaction(TxBuilder.batchTransaction(signer, c, TxBuilder.ZERO_BLOCKHASH), replaceBlockhash = true)
                .getOrElse { return Result.failure(Exception("Could not reach Solana to check the transactions: ${it.message?.take(80)}")) }
            if (sim.err != null) return Result.failure(Exception("The cluster rejected a transaction: ${sim.err.take(120)}"))
        }
        onStage(Stage.Signing)
        val result = try {
            adapter.transact(sender) { auth ->
                val walletKey = auth.accounts.firstOrNull()?.publicKey ?: throw Exception("No account returned from wallet authorization")
                if (!walletKey.contentEquals(signer)) throw Exception("The wallet returned a different account (${shortKey(Base58.encode(walletKey))}) from the one signed in.")
                val bh = rpc.getLatestBlockhash("confirmed").getOrThrow()
                val bhBytes = Base58.decode(bh.blockhash); require(bhBytes.size == 32)
                val txs = chunks.map { TxBuilder.batchTransaction(signer, it, bhBytes) }
                val r = signAndSendTransactions(transactions = txs.toTypedArray())
                r.signatures.map { Base58.encode(it) }
            }
        } catch (e: Exception) { Log.e(TAG, "publishBatch failed", e); TransactionResult.Failure<List<String>>(e.message ?: "The wallet did not complete the request.", e) }
        com.undersigned.app.wallet.WalletSession.rememberAuthToken()
        val sigs = when (result) {
            is TransactionResult.Success -> result.payload
            is TransactionResult.NoWalletFound -> return Result.failure(Exception("No Solana wallet found."))
            is TransactionResult.Failure -> return Result.failure(Exception(com.undersigned.app.wallet.WalletErrors.humanise(result.e)))
        }
        onStage(Stage.Confirming)
        val deadline = System.currentTimeMillis() + AppConfig.Ledger.CONFIRM_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            delay(2500)
            val st = rpc.getSignatureStatuses(sigs).getOrNull() ?: continue
            if (st.any { it?.err != null }) return Result.failure(Exception("A transaction failed on-chain"))
            if (st.all { it != null && (it.confirmationStatus == "confirmed" || it.confirmationStatus == "finalized") }) break
        }
        return Result.success(sigs.zip(chunks))
    }

    companion object { private const val TAG = "ReviewTx" }
}
