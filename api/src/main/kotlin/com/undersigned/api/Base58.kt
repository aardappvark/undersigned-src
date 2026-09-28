package com.undersigned.api

import java.math.BigInteger

object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val FIFTY_EIGHT = BigInteger.valueOf(58)

    fun decode(input: String): ByteArray {
        var bi = BigInteger.ZERO
        for (ch in input) {
            val index = ALPHABET.indexOf(ch)
            require(index >= 0) { "Invalid Base58 character: $ch" }
            bi = bi.multiply(FIFTY_EIGHT) + BigInteger.valueOf(index.toLong())
        }
        val bytes = bi.toByteArray()
        val leadingZeros = input.takeWhile { it == '1' }.length
        val stripped = if (bytes.isNotEmpty() && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        return ByteArray(leadingZeros) + stripped
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var bi = BigInteger(1, input)
        val sb = StringBuilder()
        while (bi > BigInteger.ZERO) {
            val (q, r) = bi.divideAndRemainder(FIFTY_EIGHT)
            sb.append(ALPHABET[r.toInt()]); bi = q
        }
        for (b in input) { if (b == 0.toByte()) sb.append('1') else break }
        return sb.reverse().toString()
    }

    /** A 32-byte Solana public key, or null. Rejects anything that is not exactly 32 bytes. */
    fun decodePubkey(input: String): ByteArray? = try {
        val trimmed = input.trim()
        if (trimmed.length !in 32..44) null else decode(trimmed).takeIf { it.size == 32 }
    } catch (_: Exception) { null }
}
