package com.undersigned.api

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Treasuries found in app code: what is read from a build, and every test a wallet must pass before it is listed. */
class FoundTreasuriesTest {
    private val treasury = "DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q"
    private val a = "7cVfgArCheMR6Cs4t6vz5rfnqd56vZq4ndaBrY5xkxXy"
    private val b = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"
    private val sgtAuthority = "GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4"

    // Measured on-chain 27 Sep 2026: the fleet treasury's SKR and USDC token accounts. Token payments land HERE, not on the wallet.
    @Test fun associatedTokenAccountsMatchTheChain() {
        assertEquals("GcV5XyhuaScv5Rp77RjpsJiGUaSFjtGdA8c4nUrsDFLk", FoundTreasuries.ata(treasury, Config.SKR_MINT, FoundTreasuries.tokenProgramOf(Config.SKR_MINT)))
        assertEquals("CadZajGB5g6XCwhpbfKKh1LTmkN6PCA6Lxo2VKtVremK", FoundTreasuries.ata(treasury, Config.USDC_MINT, FoundTreasuries.tokenProgramOf(Config.USDC_MINT)))
    }

    @Test fun addressesAreReadFromEveryKindOfFile() {
        val f = File.createTempFile("ftscan", ".apk").apply { deleteOnExit() }
        ZipOutputStream(f.outputStream()).use { z ->
            fun put(n: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(n)); z.write(bytes); z.closeEntry() }
            put("classes.dex", byteArrayOf(0, 1, 2) + "pay to $a now".toByteArray() + byteArrayOf(0))
            put("assets/index.android.bundle", "const T=\"$b\";const notAKey=\"${a}x\";".toByteArray())
            put("assets/placeholder.txt", "22222222222222222222222222222222222222222222 33333333333333333333333333333333333333333333".toByteArray())
            put("res/drawable/x.png", treasury.toByteArray())        // images are skipped
            put("META-INF/CERT.SF", treasury.toByteArray())          // signatures are skipped
        }
        assertEquals(setOf(a, b), FoundTreasuries.addresses(f))
    }

    private class FakeRpc(val payers: Map<String, Int>) : Rpc(listOf("http://127.0.0.1:9")) {
        override fun briefs(keys: List<String>) = keys.associateWith { Brief(FoundTreasuries.SYSTEM, false, 1) }
        override fun signatures(address: String, limit: Int, before: String?, until: String?): List<SignatureRow> {
            val n = payers[address] ?: 0
            return (0 until n).map { SignatureRow("$address-$it", 1_790_000_000L + it, false, null) }
        }
        override fun tx(signature: String, creditsTo: String?): TxInfo {
            val i = signature.substringAfterLast('-').toInt()
            return TxInfo(signature, "payer$i", 1_790_000_000L + i, true, emptyList(), 5000, false, emptyList(), creditsTo, 10_000_000, emptyMap())
        }
    }

    private fun db() = Db(File.createTempFile("us-ft", ".db").apply { deleteOnExit() }.path)

    @Test fun onlyWalletsPaidByEnoughPayersInOnePublishersAppsAreListed() {
        val db = db()
        val ft = FoundTreasuries(db, FakeRpc(mapOf(a to 6, b to 3, treasury to 9)), Store(0))
        fun scan(pkg: String, pub: String, vararg w: String) = db.exec("INSERT INTO apk_scan (pkg, version, at, size, wallets, publisher, note) VALUES (?, '1', 0, 1, ?, ?, '9 keys')", pkg, w.joinToString(","), pub)
        scan("com.one.app", "One Ltd", a, b, sgtAuthority)
        scan("com.one.other", "One Ltd", a)
        scan("com.two.app", "Two Ltd", treasury)
        scan("com.three.app", "Three Ltd", treasury)       // the same wallet in two publishers' apps: shared, never listed
        listOf(a, b, treasury).forEach { ft.measure(it) }
        val rows = ft.list(mapOf("com.one.app" to "One", "com.one.other" to "One Other"), emptySet(), emptySet(), emptySet())
        assertEquals(listOf("com.one.app" to a, "com.one.other" to a), rows.map { it.getString("package") to it.getString("wallet") })
        assertEquals(6, rows[0].getInt("payers"))
        // A wallet confirmed for any app, an app with a confirmed treasury, and an operator hide each remove a row.
        assertTrue(ft.list(emptyMap(), setOf(a), emptySet(), emptySet()).isEmpty())
        assertEquals(listOf("com.one.other"), ft.list(emptyMap(), emptySet(), setOf("com.one.app"), emptySet()).map { it.getString("package") })
        assertEquals(listOf("com.one.app"), ft.list(emptyMap(), emptySet(), emptySet(), setOf("treasury:com.one.other:$a")).map { it.getString("package") })
    }

    @Test fun theWalletPayingItselfIsNotAPayment() {
        val db = db()
        val rpc = object : Rpc(listOf("http://127.0.0.1:9")) {
            override fun signatures(address: String, limit: Int, before: String?, until: String?) = if (address == a) (0 until 8).map { SignatureRow("s$it", 1L + it, false, null) } else emptyList()
            override fun tx(signature: String, creditsTo: String?) = TxInfo(signature, a, 1, true, emptyList(), 5000, false, emptyList(), creditsTo, 1_000, emptyMap())
        }
        assertEquals(0, FoundTreasuries(db, rpc, Store(0)).measure(a).payers)
    }
}
