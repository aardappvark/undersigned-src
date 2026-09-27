package com.undersigned.app.ledger

import com.undersigned.app.solana.SolanaRpcClient
import com.undersigned.protocol.Payments
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Live mainnet read of the fleet treasury, run by hand: PAY_LIVE=<rpc url> writes what the board would count to
 * $PAY_LIVE_OUT. Checked against the fleet ledger (63 SeekerVerify payments carry the v1 reference and no memo).
 * Without the variable it does nothing.
 */
class PaymentBoardLiveTest {
    @Test fun live() = runBlocking {
        val rpcUrl = System.getenv("PAY_LIVE") ?: return@runBlocking
        val dir = Files.createTempDirectory("pay").toFile()
        val board = PaymentBoard(dir, SolanaRpcClient(listOf(rpcUrl)))
        val t = "DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q"
        val sb = StringBuilder()
        for (code in listOf("GpUhbtQPEEeH1QN2Z528KtZW9pZuiB3J6FGY2XPvGn3o", "SVY", "ART")) {
            val r = board.read(t, code)
            val tally = Payments.tally(r.payments, t) { false }
            val h = Prices.history()
            val paid = r.payments.filter { it.payer != t }.map { Prices.usdWhenPaid(it, h) }
            sb.append("$code payments=${tally.payments} payers=${tally.payers} sol=${tally.lamports / 1e9} tokens=${tally.tokens} complete=${r.complete} usdWhenPaid=${if (paid.all { it != null }) "%.2f".format(paid.sumOf { it!! }) else "missing ${paid.count { it == null }}"}\n")
        }
        File(System.getenv("PAY_LIVE_OUT") ?: "/tmp/pay_live.txt").writeText(sb.toString())
        dir.deleteRecursively()
    }
}
