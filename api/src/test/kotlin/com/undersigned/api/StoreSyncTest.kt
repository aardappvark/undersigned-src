package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The daily store crawl (9 Oct 2026 gaps): a listing's "updated" date moving is recorded as a release for EVERY app, not
 * only the 29 the ledger mentions; and a crawl whose star counts failed is retried within the hour, not tomorrow.
 */
class StoreSyncTest {
    private class FakeStore(val apps: List<CatalogueApp>, var ratingsUp: Boolean) : Store(0) {
        override fun catalogue() = apps
        override fun discover() = emptySet<String>()
        override fun live(pkgs: List<String>): Map<String, CatalogueApp>? = null
        override fun details(pkg: String): StoreDetails? = null
        override fun ratings(pkgs: List<String>): Map<String, List<Int>>? = if (ratingsUp) pkgs.associateWith { listOf(0, 0, 0, 0, 1) } else null
    }

    private fun db() = Db(File.createTempFile("us-store", ".db").apply { deleteOnExit() }.path).also {
        it.put("catalogue", JSONArray().put(JSONObject().put("p", "com.a.b").put("n", "App B").put("u", "2026-10-01").put("c", "Tools")).toString())
    }

    private fun changes(db: Db) = db.query("SELECT pkg, kind, old, new FROM changes") { listOf(it.getString(1), it.getString(2), it.getString(3), it.getString(4)) }

    @Test fun movedListingDateIsARelease() {
        val db = db()
        val store = FakeStore(listOf(CatalogueApp("com.a.b", "App B", "2026-10-08", "Tools"), CatalogueApp("com.c.d", "App D", "2026-10-08", "Tools")), ratingsUp = true)
        Indexer(db, Rpc(listOf("http://127.0.0.1:9")), store).syncStore(force = true)
        // com.c.d was not known before this crawl, so there is nothing to compare: no row.
        assertEquals(listOf(listOf("com.a.b", "release", "2026-10-01", "2026-10-08")), changes(db))
        // The same dates again: no second row.
        Indexer(db, Rpc(listOf("http://127.0.0.1:9")), store).syncStore(force = true)
        assertEquals(1, changes(db).size)
    }

    @Test fun failedStarCountsRetryWithinTheHour() {
        val db = db()
        val store = FakeStore(listOf(CatalogueApp("com.a.b", "App B", "2026-10-01", "Tools")), ratingsUp = false)
        val ix = Indexer(db, Rpc(listOf("http://127.0.0.1:9")), store)
        val now = System.currentTimeMillis()
        ix.syncStore(force = true)
        val due = db.get("store_synced")!!.toLong() + Config.CATALOGUE_EVERY_MS
        assertTrue(due in now..now + 3_700_000L, "next crawl due in ${(due - now) / 60_000} min, want under an hour")
        store.ratingsUp = true
        ix.syncStore(force = true)
        val dueOk = db.get("store_synced")!!.toLong() + Config.CATALOGUE_EVERY_MS
        assertTrue(dueOk - now > 23 * 3_600_000L, "a crawl that answered waits the full day")
        assertEquals(1, db.query("SELECT count(*) FROM ratings WHERE pkg = 'com.a.b'") { it.getInt(1) }.first())
    }
}
