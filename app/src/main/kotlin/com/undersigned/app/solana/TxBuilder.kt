package com.undersigned.app.solana

import com.undersigned.app.AppConfig
import com.undersigned.app.util.Base58
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AccountMeta(val key: ByteArray, val isSigner: Boolean, val isWritable: Boolean)
class Instruction(val programId: ByteArray, val accounts: List<AccountMeta>, val data: ByteArray)

/**
 * Builds the review carrier transaction (see docs/SPEC.md §1):
 *   ix[0] SPL Memo with the SRL1 record
 *   ix[1] System transfer, reviewer → reviewer, 0 lamports, with the registry PDA as an extra
 *         read-only account (fleet reference-key pattern; the System program ignores it).
 * Verified on mainnet by simulation 8 Sep 2026: err null, 19,488 CU. Nothing but the network fee leaves the wallet.
 */
object TxBuilder {
    val SYSTEM_PROGRAM_ID = ByteArray(32)
    val MEMO_PROGRAM_ID: ByteArray = Base58.decode(AppConfig.Solana.MEMO_PROGRAM)
    val REGISTRY: ByteArray = Base58.decode(AppConfig.Ledger.REGISTRY)
    val ZERO_BLOCKHASH = ByteArray(32)

    fun reviewTransaction(signer: ByteArray, memoText: String, blockhash: ByteArray): ByteArray {
        val ixs = listOf(memoInstruction(memoText), systemTransfer(signer, signer, 0L, REGISTRY))
        val msg = compileMessage(signer, blockhash, ixs)
        val tx = wrapAsTransaction(msg)
        require(tx.size <= AppConfig.Solana.MAX_TX_BYTES) { "transaction too large (${tx.size} B)" }
        return tx
    }

    /** Several records in one transaction (imports): memo instructions in order, then the carrier. Readers id them sig, sig:1, sig:2 … */
    fun batchTransaction(signer: ByteArray, memoTexts: List<String>, blockhash: ByteArray): ByteArray {
        require(memoTexts.isNotEmpty())
        val ixs = memoTexts.map { memoInstruction(it) } + systemTransfer(signer, signer, 0L, REGISTRY)
        return wrapAsTransaction(compileMessage(signer, blockhash, ixs))
    }

    /**
     * Packs whole GROUPS (a review and the use/tim records that must share its transaction) under the same limit as
     * [chunkForBatches]. A group never splits across transactions; a group too big on its own loses records from its end
     * (the extras, never the review) until it fits.
     */
    fun chunkGroups(signer: ByteArray, groups: List<List<String>>): List<List<String>> {
        fun fits(t: List<String>) = batchTransaction(signer, t, ZERO_BLOCKHASH).size <= AppConfig.Solana.MAX_TX_BYTES - 120
        val out = ArrayList<List<String>>(); var cur = ArrayList<String>()
        for (g0 in groups) {
            var g = g0; while (g.size > 1 && !fits(g)) g = g.dropLast(1)
            if (!fits(g)) continue                       // a review that cannot fit alone is not signed (the composer prevents it)
            if (fits(cur + g)) cur.addAll(g) else { if (cur.isNotEmpty()) out.add(cur); cur = ArrayList(g) }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    /** Greedy packing under the wire limit with headroom for the wallet's ComputeBudget rewrite (~120 B). */
    fun chunkForBatches(signer: ByteArray, memoTexts: List<String>): List<List<String>> {
        val out = ArrayList<List<String>>(); var cur = ArrayList<String>()
        for (t in memoTexts) {
            val trial = cur + t
            if (batchTransaction(signer, trial, ZERO_BLOCKHASH).size <= AppConfig.Solana.MAX_TX_BYTES - 120) cur = ArrayList(trial)
            else { if (cur.isNotEmpty()) out.add(cur); cur = arrayListOf(t) }
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }

    // ---- Pro payment carriers (fleet shape: transfer with the reference on it, the app memo appended LAST) ----
    val TREASURY: ByteArray = Base58.decode(AppConfig.Payment.TREASURY)
    val TOKEN_PROGRAM_ID: ByteArray = Base58.decode(AppConfig.Payment.TOKEN_PROGRAM)
    val ATA_PROGRAM_ID: ByteArray = Base58.decode(AppConfig.Payment.ATA_PROGRAM)
    val SKR_MINT: ByteArray = Base58.decode(AppConfig.Payment.SKR_MINT)

    /**
     * A payment carries EXACTLY ONE memo — the app's three-letter fleet code, appended last.
     *
     * It used to carry the licence record `SRL1|pro|12` first and the code second. Both were on-chain, but
     * `getSignaturesForAddress` summarises a transaction with only its FIRST memo, so every fleet tool that
     * scans the treasury saw `[11] SRL1|pro|12` where every other app shows `[3] RGS` — the code was invisible
     * to the dashboard and to check_memos.py (owner, 20 Sep 2026). The term is now read the fleet way, from
     * the AMOUNT paid, so nothing has to ride along in a memo.
     *
     * ix[0] System transfer signer → treasury, registry as the reference account · ix[1] memo UNS.
     * A paid LISTING adds its `SRL1|trs` record as ix[2], after UNS, so the RPC's first-memo summary still shows UNS.
     */
    fun proSolTransaction(signer: ByteArray, lamports: Long, blockhash: ByteArray, record: String? = null): ByteArray {
        require(lamports > 0)
        val ixs = listOf(systemTransfer(signer, TREASURY, lamports, REGISTRY), memoInstruction(AppConfig.Payment.PAYMENT_MEMO)) + listOfNotNull(record?.let { memoInstruction(it) })
        return wrapAsTransaction(compileMessage(signer, blockhash, ixs))
    }

    /** SKR rail, same rule: (CreateIdempotent treasury ATA) · TransferChecked with the registry as reference · memo UNS last. */
    fun proSkrTransaction(signer: ByteArray, amountRaw: Long, blockhash: ByteArray, createTreasuryAta: Boolean, record: String? = null): ByteArray {
        require(amountRaw > 0)
        val senderAta = associatedTokenAddress(signer, SKR_MINT)
        val treasuryAta = associatedTokenAddress(TREASURY, SKR_MINT)
        val ixs = ArrayList<Instruction>()
        if (createTreasuryAta) ixs.add(createAtaIdempotent(signer, treasuryAta, TREASURY, SKR_MINT))
        ixs.add(transferChecked(senderAta, SKR_MINT, treasuryAta, signer, amountRaw, AppConfig.Payment.SKR_DECIMALS, REGISTRY))
        ixs.add(memoInstruction(AppConfig.Payment.PAYMENT_MEMO))
        record?.let { ixs.add(memoInstruction(it)) }
        return wrapAsTransaction(compileMessage(signer, blockhash, ixs))
    }

    fun associatedTokenAddress(owner: ByteArray, mint: ByteArray): ByteArray = Pda.find(listOf(owner, TOKEN_PROGRAM_ID, mint), ATA_PROGRAM_ID)

    /** ATA discriminator 1 = CreateIdempotent: a plain Create fails with IllegalOwner if the account already exists. */
    fun createAtaIdempotent(payer: ByteArray, ata: ByteArray, owner: ByteArray, mint: ByteArray) = Instruction(
        ATA_PROGRAM_ID,
        listOf(AccountMeta(payer, true, true), AccountMeta(ata, false, true), AccountMeta(owner, false, false), AccountMeta(mint, false, false), AccountMeta(SYSTEM_PROGRAM_ID, false, false), AccountMeta(TOKEN_PROGRAM_ID, false, false)),
        byteArrayOf(1),
    )

    /** SPL Token TransferChecked (12): [amount u64 LE][decimals u8]; the reference rides as an extra read-only account the program ignores. */
    fun transferChecked(sourceAta: ByteArray, mint: ByteArray, destAta: ByteArray, authority: ByteArray, amount: Long, decimals: Int, reference: ByteArray?): Instruction {
        val data = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        data.put(12.toByte()); data.putLong(amount); data.put(decimals.toByte())
        val accounts = arrayListOf(AccountMeta(sourceAta, false, true), AccountMeta(mint, false, false), AccountMeta(destAta, false, true), AccountMeta(authority, true, false))
        if (reference != null) accounts.add(AccountMeta(reference, false, false))
        return Instruction(TOKEN_PROGRAM_ID, accounts, data.array())
    }

    fun systemTransfer(from: ByteArray, to: ByteArray, lamports: Long, reference: ByteArray?): Instruction {
        val data = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        data.putInt(2); data.putLong(lamports)
        val accounts = ArrayList<AccountMeta>()
        accounts.add(AccountMeta(from, true, true)); accounts.add(AccountMeta(to, false, true))
        if (reference != null) accounts.add(AccountMeta(reference, false, false))
        return Instruction(SYSTEM_PROGRAM_ID, accounts, data.array())
    }

    fun memoInstruction(text: String) = Instruction(MEMO_PROGRAM_ID, emptyList(), text.toByteArray(Charsets.UTF_8))

    /** Legacy (non-versioned) message layout. */
    fun compileMessage(feePayer: ByteArray, blockhash: ByteArray, instructions: List<Instruction>): ByteArray {
        class Slot(val key: ByteArray, var signer: Boolean, var writable: Boolean)
        val slots = LinkedHashMap<String, Slot>()
        fun keyOf(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
        slots[keyOf(feePayer)] = Slot(feePayer, signer = true, writable = true)
        for (ix in instructions) {
            for (a in ix.accounts) {
                val s = slots.getOrPut(keyOf(a.key)) { Slot(a.key, false, false) }
                s.signer = s.signer || a.isSigner; s.writable = s.writable || a.isWritable
            }
            slots.getOrPut(keyOf(ix.programId)) { Slot(ix.programId, false, false) }
        }
        val payerSlot = slots[keyOf(feePayer)]!!
        val rest = slots.values.filter { it !== payerSlot }
        val ordered = ArrayList<Slot>()
        ordered.add(payerSlot)
        ordered.addAll(rest.filter { it.signer && it.writable })
        ordered.addAll(rest.filter { it.signer && !it.writable })
        ordered.addAll(rest.filter { !it.signer && it.writable })
        ordered.addAll(rest.filter { !it.signer && !it.writable })
        val index = HashMap<String, Int>(); ordered.forEachIndexed { i, s -> index[keyOf(s.key)] = i }
        val buf = ByteBuffer.allocate(4096)
        buf.put(ordered.count { it.signer }.toByte()); buf.put(ordered.count { it.signer && !it.writable }.toByte()); buf.put(ordered.count { !it.signer && !it.writable }.toByte())
        compactU16(buf, ordered.size); ordered.forEach { buf.put(it.key) }
        buf.put(blockhash)
        compactU16(buf, instructions.size)
        for (ix in instructions) {
            buf.put(index[keyOf(ix.programId)]!!.toByte())
            compactU16(buf, ix.accounts.size); ix.accounts.forEach { buf.put(index[keyOf(it.key)]!!.toByte()) }
            compactU16(buf, ix.data.size); buf.put(ix.data)
        }
        val out = ByteArray(buf.position()); buf.rewind(); buf.get(out); return out
    }

    fun wrapAsTransaction(message: ByteArray, numSigners: Int = 1): ByteArray {
        val buf = ByteBuffer.allocate(1 + numSigners * 64 + message.size)
        buf.put(numSigners.toByte()); repeat(numSigners) { buf.put(ByteArray(64)) }; buf.put(message)
        return buf.array()
    }

    private fun compactU16(buf: ByteBuffer, value: Int) {
        when {
            value < 128 -> buf.put(value.toByte())
            value < 16384 -> { buf.put(((value and 0x7F) or 0x80).toByte()); buf.put((value shr 7).toByte()) }
            else -> { buf.put(((value and 0x7F) or 0x80).toByte()); buf.put((((value shr 7) and 0x7F) or 0x80).toByte()); buf.put((value shr 14).toByte()) }
        }
    }
}
