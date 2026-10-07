package com.undersigned.app.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The weekly dApp list check, ported from the AarDappvark Toolkit (WeeklyCatalogueCheck, 21–28 Sep 2026) on the owner's
 * word (7 Oct 2026: "this has already been built on aardappvark toolkit"). Tiered as there:
 *  - **Pro**: every Monday from 01:00 phone time plus a fixed per-phone offset of up to two hours (several phones on one
 *    network must not hit the store's rate limit together), or on the first app start of the week if the phone was off.
 *  - **Free**: the Update button; Browse says when the list is a week old.
 * The check itself is [CatalogueRepository.refreshOrThrow] (categories + Arweave portal records + a search slice, never
 * dropping an app the store still lists). Apps it lists for the first time are stamped with the week's Monday: "New".
 */
object WeeklyCatalogue {
    val RUN_DAY: DayOfWeek = DayOfWeek.MONDAY
    val RUN_AT: LocalTime = LocalTime.of(1, 0)
    private const val SPREAD_MINUTES = 120
    /** A user-started check waits this long after the previous one, so repeated taps can't add up to the rate limit. */
    const val MANUAL_COOLDOWN_MS = 10 * 60_000L
    private const val PREFS = "weekly_catalogue"

    enum class Mode { SCHEDULED, CATCH_UP, NOW }

    sealed class Outcome {
        data class Ok(val total: Int, val added: List<CatalogueApp>) : Outcome()
        data class Failed(val reason: String, val retryable: Boolean) : Outcome()
        data class Skipped(val reason: String) : Outcome()
    }

    private val lock = Mutex()
    /** Bumped when a check ends, so open screens re-read the list and its date. */
    private val _updates = MutableStateFlow(0L)
    val updates: StateFlow<Long> = _updates

    // ── pure parts (unit-tested; same rules as the Toolkit) ─────────────────────────────────────────────────────────

    /** The Monday that starts [date]'s week: the stamp a week's finds share. */
    fun weekBatch(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(RUN_DAY))

    fun isDoneForWeek(lastBatch: String?, today: LocalDate): Boolean {
        val stamp = lastBatch?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
        return weekBatch(stamp) == weekBatch(today)
    }

    /** A catch-up only once this phone's own start in the week (Monday 01:00 + offset) has passed. */
    fun catchUpDue(now: LocalDateTime, offsetMinutes: Int): Boolean =
        !now.isBefore(weekBatch(now.toLocalDate()).atTime(RUN_AT).plusMinutes(offsetMinutes.toLong()))

    fun thisWeeksRun(now: LocalDateTime): LocalDateTime {
        val monday = weekBatch(now.toLocalDate()).atTime(RUN_AT)
        return if (now.isBefore(monday)) monday.minusWeeks(1) else monday
    }

    /** This phone's next start: this week's if still ahead, else next week's, and never in a week before [notBefore]. */
    fun nextStart(now: LocalDateTime, offsetMinutes: Int, notBefore: LocalDate? = null): LocalDateTime {
        val offset = offsetMinutes.toLong()
        val thisWeek = thisWeeksRun(now).plusMinutes(offset)
        var next = if (thisWeek.isAfter(now)) thisWeek else thisWeeksRun(now).plusWeeks(1).plusMinutes(offset)
        if (notBefore != null) { val floor = weekBatch(notBefore).atTime(RUN_AT).plusMinutes(offset); if (next.isBefore(floor)) next = floor }
        return next
    }

    /** The earliest week a running job may book next: never its own (a job firing early after DST or travel). */
    fun followingWeek(bookedFor: LocalDate?, today: LocalDate): LocalDate {
        val thisWeek = weekBatch(today); val booked = bookedFor?.let { weekBatch(it) }
        return (if (booked != null && booked.isAfter(thisWeek)) booked else thisWeek).plusWeeks(1)
    }

    /** True when a list updated at [refreshedAtMs] predates this week's Monday 01:00 (Browse then offers Update). */
    fun isLastWeeks(refreshedAtMs: Long, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): Boolean =
        refreshedAtMs < thisWeeksRun(now.toLocalDateTime()).atZone(now.zone).toInstant().toEpochMilli()

    /** Past this, a Monday was missed: the age reads as a warning. */
    fun isStale(updated: LocalDate, today: LocalDate): Boolean = ChronoUnit.DAYS.between(updated, today) > 7L

    // ── state ───────────────────────────────────────────────────────────────────────────────────────────────────────

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun lastOkMs(c: Context): Long = prefs(c).getLong("last_ok_ms", 0L).takeIf { it > 0 } ?: UserPreferences.catalogueRefreshedAt
    fun lastError(c: Context): String? = prefs(c).getString("last_error", null)
    fun doneThisWeek(c: Context): Boolean = isDoneForWeek(prefs(c).getString("last_batch", null), LocalDate.now())
    fun gaveUpThisWeek(c: Context): Boolean = isDoneForWeek(prefs(c).getString("gave_up_week", null), LocalDate.now())

    /** This install's start offset, 0–119 minutes after 01:00, picked once. */
    fun offsetMinutes(c: Context): Int {
        val saved = prefs(c).getInt("offset_min", -1)
        if (saved in 0 until SPREAD_MINUTES) return saved
        return kotlin.random.Random.nextInt(SPREAD_MINUTES).also { prefs(c).edit().putInt("offset_min", it).apply() }
    }

    fun cooldownLeftMs(c: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val last = prefs(c).getLong("last_run_ms", 0L)
        return if (last <= 0L) 0L else (last + MANUAL_COOLDOWN_MS - nowMs).coerceIn(0L, MANUAL_COOLDOWN_MS)
    }

    /** When New last gained apps (ms); 0 before the first such check. */
    fun newAddedMs(c: Context): Long = prefs(c).getLong("new_added_ms", 0L)

    /** The week's New: apps first listed by this week's check (or the most recent check that added any). */
    fun newThisWeek(list: List<CatalogueApp>): List<CatalogueApp> {
        val latest = list.mapNotNull { it.addedIn }.maxOrNull() ?: return emptyList()
        return list.filter { it.addedIn == latest }
    }

    // ── the check ───────────────────────────────────────────────────────────────────────────────────────────────────

    /** One at a time; "done this week" and the cool-down are re-tested inside the lock. Never throws (cancellation aside). */
    suspend fun run(c: Context, mode: Mode, onProgress: (String) -> Unit = {}): Outcome = lock.withLock {
        val app = c.applicationContext
        when (mode) {
            Mode.SCHEDULED -> if (doneThisWeek(app)) return@withLock Outcome.Skipped("already checked this week")
            Mode.CATCH_UP -> if (doneThisWeek(app) || gaveUpThisWeek(app)) return@withLock Outcome.Skipped("already checked this week")
            Mode.NOW -> if (cooldownLeftMs(app) > 0L) return@withLock Outcome.Skipped("checked a few minutes ago")
        }
        val started = System.currentTimeMillis()
        prefs(app).edit().putLong("last_run_ms", started).remove("last_error").apply()
        val batch = weekBatch(LocalDate.now()).toString()
        val outcome: Outcome = try {
            val list = CatalogueRepository(app).refreshOrThrow(batch, onProgress).getOrThrow()
            Outcome.Ok(list.size, list.filter { it.addedIn == batch })
        } catch (e: CancellationException) { throw e
        } catch (e: CatalogueRepository.RateLimited) { Outcome.Failed("the dApp Store is refusing requests from this network for now", retryable = true)
        } catch (e: java.io.IOException) { Outcome.Failed(e.message ?: "network error", retryable = true)
        } catch (e: Exception) { Outcome.Failed(e.message ?: e.javaClass.simpleName, retryable = false) }
        prefs(app).edit().apply {
            when (outcome) {
                is Outcome.Ok -> { putLong("last_ok_ms", started); putString("last_batch", batch); putInt("last_added", outcome.added.size); remove("last_error")
                    if (outcome.added.isNotEmpty()) putLong("new_added_ms", started) }   // "added 3 days ago" (a later check that finds nothing doesn't move it)
                is Outcome.Failed -> { putString("last_error", outcome.reason); if (!outcome.retryable) putString("gave_up_week", batch) }
                is Outcome.Skipped -> Unit
            }
        }.apply()
        _updates.value = System.currentTimeMillis()
        outcome
    }
}
