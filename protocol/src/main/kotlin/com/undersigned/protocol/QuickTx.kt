package com.undersigned.protocol

import java.io.ByteArrayOutputStream

/**
 * The one transaction shape the quick-review relayer accepts, shared by the phone (which builds and signs it) and the
 * API (whose tests build it the same way), so the two can never drift: accounts [relayer (fee payer), app key (read-only
 * signer), Memo, System, registry]; one memo per record naming the app key; then a 0-lamport transfer relayer -> relayer
 * carrying the registry reference. Keys are raw 32-byte public keys.
 */
object QuickTx {
    fun message(relayer: ByteArray, appKey: ByteArray, memoProgram: ByteArray, registry: ByteArray, memoTexts: List<String>, blockhash: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        fun cu16(v: Int) { var x = v; while (true) { val b = x and 0x7f; x = x ushr 7; if (x == 0) { o.write(b); return }; o.write(b or 0x80) } }
        val keys = listOf(relayer, appKey, memoProgram, ByteArray(32), registry)
        o.write(2); o.write(1); o.write(3)
        cu16(keys.size); keys.forEach { o.write(it) }; o.write(blockhash)
        cu16(memoTexts.size + 1)
        memoTexts.forEach { t -> o.write(2); cu16(1); o.write(1); val d = t.toByteArray(Charsets.UTF_8); cu16(d.size); o.write(d) }
        o.write(3); cu16(3); o.write(0); o.write(0); o.write(4); cu16(12); o.write(byteArrayOf(2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        return o.toByteArray()
    }

    /** The app key's signature in the second slot; the first left empty for the relayer. */
    fun transaction(message: ByteArray, appSignature: ByteArray): ByteArray =
        ByteArrayOutputStream().apply { write(2); write(ByteArray(64)); write(appSignature); write(message) }.toByteArray()
}
