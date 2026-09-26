package com.undersigned.protocol

import java.io.File
import kotlin.test.Test

/**
 * Runs the detector on saved real store reviews when BURST_DATA points at a TSV file (package, epoch seconds, stars, text) and writes
 * what it finds next to it. A measurement harness, not a gate: without the variable it does nothing.
 */
class RealDataBurstsTest {
    @Test fun realData() {
        val path = System.getenv("BURST_DATA") ?: return
        val rows = File(path).readLines().filter { it.isNotBlank() }.map { it.split('\t', limit = 4) }
        val sb = StringBuilder()
        for ((pkg, list) in rows.groupBy { it[0] }) {
            val pts = list.map { ReviewBursts.Point(it[1].toLong(), it[2].toInt(), it.getOrNull(3)) }
            val f = ReviewBursts.find(pts)
            sb.append("$pkg read=${f.checked} bursts=${f.bursts.map { "${it.kind} ${java.time.Instant.ofEpochSecond(it.start).toString().take(10)}..${java.time.Instant.ofEpochSecond(it.end).toString().take(10)} n=${it.count}/${it.total} usual=${"%.1f".format(it.expected)} same=${it.sameText}" }}\n")
        }
        File("$path.out").writeText(sb.toString())
    }
}
