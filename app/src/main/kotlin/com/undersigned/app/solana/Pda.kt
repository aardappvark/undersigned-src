package com.undersigned.app.solana

import java.math.BigInteger
import java.security.MessageDigest

/** Program-derived addresses, the same way the runtime derives them (first off-curve bump from 255 down). */
object Pda {
    fun find(seeds: List<ByteArray>, programId: ByteArray): ByteArray {
        for (bump in 255 downTo 0) {
            val md = MessageDigest.getInstance("SHA-256")
            seeds.forEach { md.update(it) }
            md.update(byteArrayOf(bump.toByte()))
            md.update(programId)
            md.update("ProgramDerivedAddress".toByteArray())
            val candidate = md.digest()
            if (!isOnCurve(candidate)) return candidate
        }
        throw IllegalStateException("No PDA bump found")
    }

    private val P = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
    private val D = BigInteger("-121665").multiply(BigInteger("121666").modInverse(P)).mod(P)

    fun isOnCurve(point: ByteArray): Boolean {
        val yCopy = point.copyOf(); yCopy[31] = (yCopy[31].toInt() and 0x7F).toByte()
        val y = BigInteger(1, yCopy.reversedArray()).mod(P)
        val y2 = y.multiply(y).mod(P)
        val num = y2.subtract(BigInteger.ONE).mod(P)
        val den = D.multiply(y2).add(BigInteger.ONE).mod(P)
        val x2 = num.multiply(den.modInverse(P)).mod(P)
        if (x2 == BigInteger.ZERO) return true
        return x2.modPow(P.subtract(BigInteger.ONE).divide(BigInteger.valueOf(2)), P) == BigInteger.ONE
    }
}
