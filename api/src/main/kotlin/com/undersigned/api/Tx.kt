package com.undersigned.api

import java.nio.ByteBuffer
import java.nio.ByteOrder

class AccountMeta(val key: ByteArray, val isSigner: Boolean, val isWritable: Boolean)
class Instruction(val programId: ByteArray, val accounts: List<AccountMeta>, val data: ByteArray)

/**
 * The review carrier transaction, byte for byte the app's (TxBuilder; docs/SPEC.md §1): ix[0] the SRL1 memo, ix[1] a
 * 0-lamport transfer from the signer to itself naming the registry as an extra read-only account. Built unsigned for a
 * browser wallet to sign; nothing but the network fee leaves the wallet. Copied, not re-derived, from the app.
 */
object Tx {
    val SYSTEM_PROGRAM_ID = ByteArray(32)
    val MEMO_PROGRAM_ID: ByteArray = Base58.decode(Config.MEMO_PROGRAM)
    val REGISTRY: ByteArray = Base58.decode(Config.REGISTRY)
    const val MAX_TX_BYTES = 1232
    /** Room left for a wallet that adds its own compute-budget or priority-fee instructions before it signs. */
    const val BATCH_BYTES = 1100

    fun reviewTransaction(signer: ByteArray, memoText: String, blockhash: ByteArray): ByteArray {
        val tx = wrapAsTransaction(compileMessage(signer, blockhash, listOf(memoInstruction(memoText), systemTransfer(signer, signer, 0L, REGISTRY))))
        require(tx.size <= MAX_TX_BYTES) { "transaction too large (${tx.size} B)" }
        return tx
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

    /**
     * Several memos in as few transactions as fit, each carrying the registry reference (so the index finds it). The order
     * is kept, so a memo's record id is "<signature>:<index in its transaction>".
     */
    fun memoBatches(signer: ByteArray, memos: List<String>, blockhash: ByteArray, maxBytes: Int = BATCH_BYTES): List<ByteArray> {
        val out = ArrayList<ByteArray>(); var cur = ArrayList<String>()
        fun build(l: List<String>) = wrapAsTransaction(compileMessage(signer, blockhash, l.map { memoInstruction(it) } + systemTransfer(signer, signer, 0L, REGISTRY)))
        for (m in memos) {
            if (cur.isNotEmpty() && build(cur + m).size > maxBytes) { out.add(build(cur)); cur = ArrayList() }
            cur.add(m); require(build(cur).size <= maxBytes) { "one memo is too large" }
        }
        if (cur.isNotEmpty()) out.add(build(cur))
        return out
    }

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
