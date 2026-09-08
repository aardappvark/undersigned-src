package com.undersigned.app.solana

import android.util.Base64
import com.undersigned.app.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal read-only Solana JSON-RPC client. Fails over across [endpoints] on any error, including an
 * endpoint that refuses a method. The wallet submits transactions; this client only reads and simulates.
 */
class SolanaRpcClient(private val endpoints: List<String> = AppConfig.Api.RPC_ENDPOINTS) {

    suspend fun getLatestBlockhash(commitment: String = "confirmed"): Result<Blockhash> = rpc {
        val v = call("getLatestBlockhash", JSONArray().put(JSONObject().put("commitment", commitment))).getJSONObject("result").getJSONObject("value")
        Blockhash(v.getString("blockhash"), v.optLong("lastValidBlockHeight", 0L))
    }

    suspend fun getBalance(pubkey: String): Result<Long> = rpc {
        call("getBalance", JSONArray().put(pubkey).put(JSONObject().put("commitment", "confirmed"))).getJSONObject("result").getLong("value")
    }

    /** Newest first. [before] pages backwards; [until] stops at an already-seen signature. */
    suspend fun getSignaturesForAddress(address: String, limit: Int = AppConfig.Ledger.PAGE, before: String? = null, until: String? = null): Result<List<SignatureRow>> = rpc {
        val cfg = JSONObject().put("limit", limit).put("commitment", "confirmed")
        if (before != null) cfg.put("before", before)
        if (until != null) cfg.put("until", until)
        val arr = call("getSignaturesForAddress", JSONArray().put(address).put(cfg)).optJSONArray("result") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val sig = o.optString("signature").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val err = o.opt("err").let { it != null && it != JSONObject.NULL }
            val memo = o.opt("memo")?.takeIf { it != JSONObject.NULL }?.toString()
            SignatureRow(sig, o.optLong("blockTime", 0L), err, memo)
        }
    }

    /** Fee payer + memo text of a confirmed transaction (jsonParsed so the memo arrives as a string). */
    suspend fun getLedgerTx(signature: String): Result<LedgerTx?> = rpc {
        val tx = call("getTransaction", JSONArray().put(signature).put(JSONObject().put("encoding", "jsonParsed").put("maxSupportedTransactionVersion", 0).put("commitment", "confirmed")))
            .optJSONObject("result") ?: return@rpc null
        val meta = tx.optJSONObject("meta")
        if (meta != null && meta.opt("err") != null && meta.opt("err") != JSONObject.NULL) return@rpc null
        val msg = tx.optJSONObject("transaction")?.optJSONObject("message") ?: return@rpc null
        val keys = msg.optJSONArray("accountKeys") ?: return@rpc null
        val first = keys.optJSONObject(0)?.optString("pubkey") ?: keys.optString(0)
        if (first.isNullOrEmpty()) return@rpc null
        val memos = ArrayList<String>(); var lighthouse = false; var computeBudget = false
        val ixs = msg.optJSONArray("instructions") ?: JSONArray()
        for (i in 0 until ixs.length()) {
            val ix = ixs.optJSONObject(i) ?: continue
            val pid = ix.optString("programId")
            if (pid == AppConfig.Solana.MEMO_PROGRAM || ix.optString("program") == "spl-memo") { val parsed = ix.opt("parsed"); if (parsed is String) memos.add(parsed) }
            if (pid == AppConfig.Solana.LIGHTHOUSE_PROGRAM) lighthouse = true
            if (pid == AppConfig.Solana.COMPUTE_BUDGET_PROGRAM) computeBudget = true
        }
        LedgerTx(first, tx.optLong("blockTime", 0L), memos, meta?.optLong("fee", 0L) ?: 0L, lighthouse && computeBudget)
    }

    /** Token-2022 token accounts of [owner], raw base64 data (for the generic attestation check). */
    suspend fun getToken2022AccountsBase64(owner: String): Result<List<Pair<String, ByteArray>>> = rpc {
        val params = JSONArray().put(owner).put(JSONObject().put("programId", "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")).put(JSONObject().put("encoding", "base64").put("commitment", "confirmed"))
        val arr = call("getTokenAccountsByOwner", params).getJSONObject("result").optJSONArray("value") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i -> val o = arr.optJSONObject(i) ?: return@mapNotNull null; val d = o.optJSONObject("account")?.optJSONArray("data")?.optString(0) ?: return@mapNotNull null; o.optString("pubkey") to Base64.decode(d, Base64.DEFAULT) }
    }

    suspend fun getAccountDataBase64(pubkey: String): Result<ByteArray?> = rpc {
        val v = call("getAccountInfo", JSONArray().put(pubkey).put(JSONObject().put("encoding", "base64").put("commitment", "confirmed"))).getJSONObject("result").opt("value")
        if (v == null || v == JSONObject.NULL) null
        else Base64.decode((v as JSONObject).getJSONArray("data").getString(0), Base64.DEFAULT)
    }

    /** getProgramAccounts with the given filters, base64 data. Public pools may refuse it; that fails over. */
    suspend fun getProgramAccountsBase64(program: String, filters: JSONArray): Result<List<Pair<String, ByteArray>>> = rpc {
        val arr = call("getProgramAccounts", JSONArray().put(program).put(JSONObject().put("encoding", "base64").put("commitment", "confirmed").put("filters", filters))).optJSONArray("result") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val pk = o.optString("pubkey"); val data = o.optJSONObject("account")?.optJSONArray("data")?.optString(0) ?: return@mapNotNull null
            pk to Base64.decode(data, Base64.DEFAULT)
        }
    }

    /** Simulates a fully serialized transaction (signature bytes may be zero). */
    suspend fun simulateTransaction(tx: ByteArray, replaceBlockhash: Boolean): Result<SimulationResult> = rpc {
        val cfg = JSONObject().put("encoding", "base64").put("sigVerify", false).put("replaceRecentBlockhash", replaceBlockhash).put("commitment", "confirmed")
        val value = call("simulateTransaction", JSONArray().put(Base64.encodeToString(tx, Base64.NO_WRAP)).put(cfg)).getJSONObject("result").getJSONObject("value")
        val errObj = value.opt("err")
        val err = if (errObj == null || errObj == JSONObject.NULL) null else errObj.toString()
        var failedIx: Int? = null
        if (errObj is JSONObject) errObj.optJSONArray("InstructionError")?.let { ie -> if (ie.length() >= 1) failedIx = ie.optInt(0, -1).takeIf { it >= 0 } }
        val logs = ArrayList<String>(); value.optJSONArray("logs")?.let { for (i in 0 until it.length()) logs.add(it.optString(i)) }
        SimulationResult(err, failedIx, logs, value.optLong("unitsConsumed", 0L))
    }

    suspend fun getSignatureStatuses(signatures: List<String>): Result<List<SignatureStatus?>> = rpc {
        val arr = JSONArray(); signatures.forEach { arr.put(it) }
        val value = call("getSignatureStatuses", JSONArray().put(arr).put(JSONObject().put("searchTransactionHistory", true))).getJSONObject("result").getJSONArray("value")
        (0 until value.length()).map { i ->
            val o = value.optJSONObject(i) ?: return@map null
            val err = o.opt("err")
            SignatureStatus(o.optString("confirmationStatus", ""), if (err == null || err == JSONObject.NULL) null else err.toString())
        }
    }

    private suspend fun <T> rpc(block: () -> T): Result<T> = withContext(Dispatchers.IO) { try { Result.success(block()) } catch (e: Exception) { Result.failure(e) } }

    private fun call(method: String, params: JSONArray): JSONObject {
        val body = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString()
        var last: Exception? = null
        for (endpoint in endpoints) {
            try {
                val resp = callEndpoint(body, endpoint)
                if (!resp.isNull("error")) throw RpcException(resp.optJSONObject("error")?.optString("message") ?: "RPC error")
                return resp
            } catch (e: Exception) { last = e }
        }
        throw last ?: RpcException("All RPC endpoints failed")
    }

    private fun callEndpoint(requestJson: String, endpoint: String): JSONObject {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"; conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Undersigned/1.0 (Android)")
            conn.connectTimeout = 8000; conn.readTimeout = 15000; conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(requestJson); it.flush() }
            val code = conn.responseCode
            val host = endpoint.substringBefore("?")
            if (code == 429) throw RpcException("HTTP 429: rate limited by $host")
            if (code != 200) throw RpcException("HTTP $code from $host")
            return BufferedReader(InputStreamReader(conn.inputStream)).use { JSONObject(it.readText()) }
        } finally { conn.disconnect() }
    }
}

class RpcException(message: String) : Exception(message)
