package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

data class SignatureRow(val signature: String, val blockTime: Long, val err: Boolean, val memo: String?)

/**
 * One confirmed transaction, as the server needs it. [credits] is what it credited [creditsTo]: lamports on the wallet and
 * raw units per mint on token accounts the wallet owns — from balances, never from a memo.
 */
data class TxInfo(
    val signature: String, val feePayer: String, val blockTime: Long, val ok: Boolean, val memos: List<String>, val fee: Long,
    /** Lighthouse + ComputeBudget instructions: the shape the Seeker's own wallet adds. A hint, not proof. */
    val seekerShape: Boolean, val accountKeys: List<String>, val creditsTo: String?, val lamportsIn: Long, val tokensIn: Map<String, Long>,
)

class RpcException(message: String) : Exception(message)

/** Read-only JSON-RPC client over a failover pool (port of the app's SolanaRpcClient, blocking). */
open class Rpc(private val endpoints: List<String> = Config.RPC) {

    open fun signatures(address: String, limit: Int = 1000, before: String? = null, until: String? = null): List<SignatureRow> {
        val cfg = JSONObject().put("limit", limit).put("commitment", "confirmed")
        before?.let { cfg.put("before", it) }; until?.let { cfg.put("until", it) }
        val arr = call("getSignaturesForAddress", JSONArray().put(address).put(cfg)).optJSONArray("result") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val sig = o.optString("signature").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SignatureRow(sig, o.optLong("blockTime", 0L), o.opt("err").let { it != null && it != JSONObject.NULL }, o.opt("memo")?.takeIf { it != JSONObject.NULL }?.toString())
        }
    }

    /** Null when no node has the transaction (unknown, NOT "paid nothing": callers retry). */
    open fun tx(signature: String, creditsTo: String? = Config.TREASURY): TxInfo? {
        // Version 1 transactions exist on mainnet (10 Oct 2026: 868 payments into one found wallet stalled the history read for
        // hours, refused with -32015 at version 0). The parsed shape is the same; a version the RPC cannot serve still returns null.
        val tx = call("getTransaction", JSONArray().put(signature).put(JSONObject().put("encoding", "jsonParsed").put("maxSupportedTransactionVersion", 1).put("commitment", "confirmed")))
            .optJSONObject("result") ?: return null
        return parseTx(signature, tx, creditsTo)
    }

    /** The money path, on its own so a fixture can test it: what a jsonParsed transaction credited [creditsTo], from balances. */
    fun parseTx(signature: String, tx: JSONObject, creditsTo: String?): TxInfo? {
        val meta = tx.optJSONObject("meta")
        val ok = meta != null && (meta.opt("err") == null || meta.opt("err") == JSONObject.NULL)
        val msg = tx.optJSONObject("transaction")?.optJSONObject("message") ?: return null
        val keysArr = msg.optJSONArray("accountKeys") ?: return null
        val keys = (0 until keysArr.length()).map { i -> keysArr.optJSONObject(i)?.optString("pubkey") ?: keysArr.optString(i) }
        // A relayed quick review is authored by its second signer, not the relayer that paid (protocol Registry.author).
        val signers = (0 until keysArr.length()).mapNotNull { i -> keysArr.optJSONObject(i)?.takeIf { it.optBoolean("signer") }?.optString("pubkey") }
        val payer = (com.undersigned.protocol.Registry.author(signers) ?: keys.firstOrNull())?.takeIf { it.isNotEmpty() } ?: return null
        val memos = ArrayList<String>(); var lighthouse = false; var computeBudget = false
        val ixs = msg.optJSONArray("instructions") ?: JSONArray()
        for (i in 0 until ixs.length()) {
            val ix = ixs.optJSONObject(i) ?: continue
            val pid = ix.optString("programId")
            if (pid == Config.MEMO_PROGRAM || ix.optString("program") == "spl-memo") (ix.opt("parsed") as? String)?.let { memos.add(it) }
            if (pid == "L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95") lighthouse = true
            if (pid == "ComputeBudget111111111111111111111111111111") computeBudget = true
        }
        var lamportsIn = 0L; var tokensIn: Map<String, Long> = emptyMap()
        if (meta != null && creditsTo != null) {
            val t = keys.indexOf(creditsTo)
            if (t >= 0) lamportsIn = ((meta.optJSONArray("postBalances")?.optLong(t, 0L) ?: 0L) - (meta.optJSONArray("preBalances")?.optLong(t, 0L) ?: 0L)).coerceAtLeast(0L)
            fun owned(arr: JSONArray?): Map<String, Long> {
                val m = HashMap<String, Long>(); arr ?: return m
                for (i in 0 until arr.length()) { val o = arr.optJSONObject(i) ?: continue; if (o.optString("owner") != creditsTo) continue
                    m[o.optString("mint")] = (m[o.optString("mint")] ?: 0L) + (o.optJSONObject("uiTokenAmount")?.optString("amount", "0")?.toLongOrNull() ?: 0L) }
                return m
            }
            val pre = owned(meta.optJSONArray("preTokenBalances")); val post = owned(meta.optJSONArray("postTokenBalances"))
            tokensIn = post.mapValues { (k, v) -> v - (pre[k] ?: 0L) }.filterValues { it > 0 }
        }
        return TxInfo(signature, payer, tx.optLong("blockTime", 0L), ok, memos, meta?.optLong("fee", 0L) ?: 0L, lighthouse && computeBudget, keys, creditsTo, lamportsIn, tokensIn)
    }

    open fun token2022Accounts(owner: String): List<ByteArray> {
        val params = JSONArray().put(owner).put(JSONObject().put("programId", "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")).put(JSONObject().put("encoding", "base64").put("commitment", "confirmed"))
        val arr = call("getTokenAccountsByOwner", params).getJSONObject("result").optJSONArray("value") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optJSONObject("account")?.optJSONArray("data")?.optString(0)?.let { Base64.getDecoder().decode(it) } }
    }

    open fun accountData(pubkey: String): ByteArray? {
        val v = call("getAccountInfo", JSONArray().put(pubkey).put(JSONObject().put("encoding", "base64").put("commitment", "confirmed"))).getJSONObject("result").opt("value")
        return if (v == null || v == JSONObject.NULL) null else Base64.getDecoder().decode((v as JSONObject).getJSONArray("data").getString(0))
    }

    /**
     * Does [address] hold a Seeker Genesis Token? The fleet's seeker-verify test, which the app runs: a Token-2022 token
     * (amount ≥ 1) whose mint passes THREE checks — mint authority, MetadataPointer authority + address, and
     * TokenGroupMember group. Layout: token account = mint[0..32] owner[32..64] amount u64 LE[64..72].
     */
    fun holdsSeekerGenesisToken(address: String): Boolean {
        val mints = token2022Accounts(address).mapNotNull { d ->
            if (d.size < 72 || ByteBuffer.wrap(d, 64, 8).order(ByteOrder.LITTLE_ENDIAN).long <= 0L) null else Base58.encode(d.copyOfRange(0, 32))
        }.distinct()
        return mints.any { m -> accountData(m)?.let { Sgt.isValid(it) } == true }
    }

    /** Accounts of [program] with [size] bytes whose bytes at [offset] are [base58]; base64 data. Public pools may refuse it. */
    open fun programAccounts(program: String, size: Int, offset: Int, base58: String): List<ByteArray> {
        val filters = JSONArray().put(JSONObject().put("dataSize", size)).put(JSONObject().put("memcmp", JSONObject().put("offset", offset).put("bytes", base58)))
        val arr = call("getProgramAccounts", JSONArray().put(program).put(JSONObject().put("encoding", "base64").put("commitment", "confirmed").put("filters", filters))).optJSONArray("result") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optJSONObject("account")?.optJSONArray("data")?.optString(0)?.let { Base64.getDecoder().decode(it) } }
    }

    /** (lamports, first 12 bytes) of every native stake account whose withdrawer is [wallet] (StakeView.solStaked). */
    open fun stakeAccounts(wallet: String): List<Pair<Long, ByteArray>> {
        val filters = JSONArray().put(JSONObject().put("dataSize", com.undersigned.protocol.StakeView.STAKE_ACCOUNT_SIZE))
            .put(JSONObject().put("memcmp", JSONObject().put("offset", com.undersigned.protocol.StakeView.WITHDRAWER_OFFSET).put("bytes", wallet)))
        val arr = call("getProgramAccounts", JSONArray().put(com.undersigned.protocol.StakeView.STAKE_PROGRAM).put(JSONObject().put("encoding", "base64").put("commitment", "confirmed")
            .put("filters", filters).put("dataSlice", JSONObject().put("offset", 0).put("length", 12)))).optJSONArray("result") ?: error("no result")
        return (0 until arr.length()).mapNotNull { i -> val a = arr.optJSONObject(i)?.optJSONObject("account") ?: return@mapNotNull null
            a.optLong("lamports") to Base64.getDecoder().decode(a.optJSONArray("data")?.optString(0) ?: "") }
    }

    open fun latestBlockhash(): String = call("getLatestBlockhash", JSONArray().put(JSONObject().put("commitment", "confirmed"))).getJSONObject("result").getJSONObject("value").getString("blockhash")

    /** Pubkeys of [program]'s accounts matching [filters] (getProgramAccounts, data sliced away). */
    open fun programAccountKeys(program: String, filters: JSONArray): List<String> {
        val arr = call("getProgramAccounts", JSONArray().put(program).put(JSONObject().put("encoding", "base64").put("commitment", "confirmed").put("filters", filters)
            .put("dataSlice", JSONObject().put("offset", 0).put("length", 0)))).optJSONArray("result") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("pubkey")?.takeIf { k -> k.isNotEmpty() } }
    }

    /** Submits a signed transaction (base64); returns its signature. */
    open fun sendTransaction(base64: String): String =
        call("sendTransaction", JSONArray().put(base64).put(JSONObject().put("encoding", "base64").put("preflightCommitment", "confirmed"))).getString("result")

    /** Owner, executable flag and balance of each account (null: no account), up to 100 per call, with no account data. */
    data class Brief(val owner: String, val executable: Boolean, val lamports: Long)
    open fun briefs(keys: List<String>): Map<String, Brief?> = keys.chunked(100).flatMap { ch ->
        val v = call("getMultipleAccounts", JSONArray().put(JSONArray(ch)).put(JSONObject().put("encoding", "base64").put("dataSlice", JSONObject().put("offset", 0).put("length", 0))))
            .optJSONObject("result")?.optJSONArray("value") ?: JSONArray()
        ch.mapIndexed { i, k -> k to v.optJSONObject(i)?.let { o -> Brief(o.optString("owner"), o.optBoolean("executable"), o.optLong("lamports")) } }
    }.toMap()

    private fun call(method: String, params: JSONArray): JSONObject {
        val body = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString()
        var last: Exception? = null
        for (endpoint in endpoints) {
            try {
                val resp = post(endpoint, body)
                if (!resp.isNull("error")) throw RpcException(resp.optJSONObject("error")?.optString("message") ?: "RPC error")
                return resp
            } catch (e: Exception) { last = e }
        }
        throw last ?: RpcException("All RPC endpoints failed")
    }

    private fun post(endpoint: String, body: String): JSONObject {
        val c = URL(endpoint).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", "Undersigned-API/1.0")
            c.connectTimeout = 8000; c.readTimeout = 20000; c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
            val host = URL(endpoint).host   // never the path or query: a provider key can sit in either
            if (c.responseCode != 200) throw RpcException("HTTP ${c.responseCode} from $host")
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    }
}
