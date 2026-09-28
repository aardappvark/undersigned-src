package com.undersigned.app.data

import com.undersigned.protocol.RatingSnapshots
import java.io.File

/**
 * Store-wide daily snapshots of every app's star counts (Store watch). One small TSV per UTC day, "pkg<TAB>time<TAB>s1..s5",
 * about 45 KB for the whole catalogue; the newest [KEEP_DAYS] days are kept. Public store data, never uploaded.
 */
class StoreRatingsArchive(private val dir: File) {
    init { dir.mkdirs() }
    @Volatile private var cache: Map<String, List<RatingSnapshots.Snapshot>>? = null

    fun record(counts: Map<String, List<Int>>, nowSec: Long) {
        if (counts.isEmpty()) return
        val day = java.time.Instant.ofEpochSecond(nowSec).toString().take(10)
        val sb = StringBuilder()
        counts.forEach { (pkg, c) -> if (c.size == 5 && '\t' !in pkg) sb.append(pkg).append('\t').append(nowSec).append('\t').append(c.joinToString("\t")).append('\n') }
        File(dir, "$day.tsv").writeText(sb.toString())
        dir.listFiles { f -> f.name.endsWith(".tsv") }?.sortedByDescending { it.name }?.drop(KEEP_DAYS)?.forEach { it.delete() }
        cache = null
    }

    /** Adds [counts] to today's file, keeping every app already recorded today (a partial read never erases the day). */
    @Synchronized fun merge(counts: Map<String, List<Int>>, nowSec: Long) {
        if (counts.isEmpty()) return
        val day = java.time.Instant.ofEpochSecond(nowSec).toString().take(10)
        val f = File(dir, "$day.tsv")
        val rows = LinkedHashMap<String, String>()
        if (f.exists()) f.forEachLine { l -> l.substringBefore('\t').takeIf { it.isNotEmpty() }?.let { rows[it] = l } }
        counts.forEach { (pkg, c) -> if (c.size == 5 && '\t' !in pkg) rows[pkg] = pkg + "\t" + nowSec + "\t" + c.joinToString("\t") }
        f.writeText(rows.values.joinToString("\n", postfix = "\n"))
        cache = null
    }

    /** Days recorded, oldest first (yyyy-mm-dd). */
    fun days(): List<String> = dir.listFiles { f -> f.name.endsWith(".tsv") }?.map { it.name.removeSuffix(".tsv") }?.sorted() ?: emptyList()

    fun all(): Map<String, List<RatingSnapshots.Snapshot>> = cache ?: run {
        val out = HashMap<String, MutableList<RatingSnapshots.Snapshot>>()
        for (d in days()) File(dir, "$d.tsv").forEachLine { line ->
            val f = line.split('\t'); if (f.size != 7) return@forEachLine
            val t = f[1].toLongOrNull() ?: return@forEachLine
            val c = f.subList(2, 7).map { it.toIntOrNull() ?: return@forEachLine }
            out.getOrPut(f[0]) { ArrayList() }.add(RatingSnapshots.Snapshot(t, c))
        }
        out.also { cache = it }
    }

    fun snapshots(pkg: String): List<RatingSnapshots.Snapshot> = all()[pkg].orEmpty()

    companion object { const val KEEP_DAYS = 60 }
}
