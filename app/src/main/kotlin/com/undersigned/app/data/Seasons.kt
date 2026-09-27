package com.undersigned.app.data

import android.content.Context
import org.json.JSONObject

/**
 * Reward periods (assets/seasons.json): windows in which dApp Store activity earned rewards, when installs and reviews
 * were worth something beyond the app itself. A review written inside one is tagged with its name — a fact about WHEN,
 * never a judgement of the review. Only bounded periods are listed; an open-ended season would tag everything.
 */
object Seasons {
    data class Season(val id: String, val label: String, val startSec: Long, val endSec: Long, val approximate: Boolean)

    fun parse(json: String): List<Season> = runCatching {
        val arr = JSONObject(json).getJSONArray("seasons")
        (0 until arr.length()).map { i -> val o = arr.getJSONObject(i)
            val start = java.time.LocalDate.parse(o.getString("start")).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
            val end = java.time.LocalDate.parse(o.getString("end")).plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond() - 1
            Season(o.getString("id"), o.getString("label"), start, end, o.optBoolean("approximate")) }
    }.getOrDefault(emptyList())

    @Volatile private var loaded: List<Season>? = null
    fun all(ctx: Context): List<Season> = loaded ?: parse(runCatching { ctx.assets.open("seasons.json").bufferedReader().use { it.readText() } }.getOrDefault("{}")).also { loaded = it }

    /** The period [epochSec] falls in; the narrower one wins when two overlap. */
    fun at(epochSec: Long, seasons: List<Season>): Season? = seasons.filter { epochSec in it.startSec..it.endSec }.minByOrNull { it.endSec - it.startSec }
}
