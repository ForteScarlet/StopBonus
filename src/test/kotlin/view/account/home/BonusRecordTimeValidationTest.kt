package view.account.home

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import picker.core.PickerEnvironment
import picker.core.PickerIssueCodes
import picker.core.sampleNow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BonusRecordTimeValidationTest {
    private val environment = PickerEnvironment(
        clock = Clock.fixed(
            Instant.parse("2026-09-06T01:00:00Z"),
            ZoneId.of("Asia/Shanghai"),
        ),
        zoneId = ZoneId.of("Asia/Shanghai"),
    )

    @Test
    fun validRecordDurationIsMeasuredBetweenInstants() {
        val start = Instant.parse("2026-09-05T16:00:00Z")
        val end = Instant.parse("2026-09-05T17:30:01Z")

        val result = validateRecordTimes(start, end, environment, environment.sampleNow())

        val valid = assertIs<RecordTimesResult.Valid>(result)
        assertEquals(Duration.ofMinutes(90).plusSeconds(1), valid.times.duration)
    }

    @Test
    fun missingAndReversedValuesIdentifyTheFieldToFix() {
        val now = environment.sampleNow()
        val start = Instant.parse("2026-09-05T16:00:00Z")

        assertEquals(
            PickerIssueCodes.Required,
            assertIs<RecordTimesResult.Invalid>(
                validateRecordTimes(null, null, environment, now)
            ).issue.code,
        )
        assertEquals(
            RecordTimeField.End,
            assertIs<RecordTimesResult.Invalid>(
                validateRecordTimes(start, null, environment, now)
            ).field,
        )
        assertEquals(
            PickerIssueCodes.EndBeforeStart,
            assertIs<RecordTimesResult.Invalid>(
                validateRecordTimes(start, start, environment, now)
            ).issue.code,
        )
    }

    @Test
    fun recordTimesRejectValuesBefore1900AndInTheFuture() {
        val now = environment.sampleNow()
        val beforeMin = Instant.parse("1899-12-31T15:50:00Z")
        val future = now.instant.plusSeconds(1)

        assertEquals(
            PickerIssueCodes.BeforeMin,
            assertIs<RecordTimesResult.Invalid>(
                validateRecordTimes(beforeMin, now.instant, environment, now)
            ).issue.code,
        )
        assertEquals(
            PickerIssueCodes.AfterMax,
            assertIs<RecordTimesResult.Invalid>(
                validateRecordTimes(now.instant, future, environment, now)
            ).issue.code,
        )
    }

    @Test
    fun recordDurationUsesTimelineOrderAcrossFallBackOverlap() {
        val now = environment.copy(
            clock = Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), environment.zoneId),
        ).sampleNow()
        val start = java.time.LocalDateTime.of(2026, 11, 1, 1, 50)
            .toInstant(java.time.ZoneOffset.ofHours(-4))
        val end = java.time.LocalDateTime.of(2026, 11, 1, 1, 10)
            .toInstant(java.time.ZoneOffset.ofHours(-5))

        val valid = assertIs<RecordTimesResult.Valid>(
            validateRecordTimes(start, end, environment, now),
        )

        assertEquals(Duration.ofMinutes(20), valid.duration)
    }

    @Test
    fun recordDurationRejectsAChronologicallyEarlierEndAcrossFallBackOverlap() {
        val now = environment.copy(
            clock = Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), environment.zoneId),
        ).sampleNow()
        val start = java.time.LocalDateTime.of(2026, 11, 1, 1, 10)
            .toInstant(java.time.ZoneOffset.ofHours(-5))
        val end = java.time.LocalDateTime.of(2026, 11, 1, 1, 50)
            .toInstant(java.time.ZoneOffset.ofHours(-4))

        assertEquals(
            PickerIssueCodes.EndBeforeStart,
            assertIs<RecordTimesResult.Invalid>(
                validateRecordTimes(start, end, environment, now),
            ).issue.code,
        )
    }
}
