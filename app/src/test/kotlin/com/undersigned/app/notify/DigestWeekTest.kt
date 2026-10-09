package com.undersigned.app.notify

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The phone keys "digest posted" by the same ISO week, in UTC, as the API's /v1/digest, so each week posts once. */
class DigestWeekTest {
    @Test fun midWeek() = assertEquals("2026-W41", LedgerWatchWorker.isoWeek(Instant.parse("2026-10-09T12:00:00Z")))

    // 1 Jan 2027 is a Friday, so it still belongs to 2026's week 53; a calendar-year key would post twice.
    @Test fun newYearBelongsToLastIsoWeek() = assertEquals("2026-W53", LedgerWatchWorker.isoWeek(Instant.parse("2027-01-01T08:00:00Z")))

    // Monday 00:00 UTC starts the next week; Sunday 23:59 UTC is still the old one, whatever the phone's time zone.
    @Test fun weekTurnsAtMondayUtc() {
        assertEquals("2026-W41", LedgerWatchWorker.isoWeek(Instant.parse("2026-10-11T23:59:59Z")))
        assertEquals("2026-W42", LedgerWatchWorker.isoWeek(Instant.parse("2026-10-12T00:00:00Z")))
    }
}
