package picker.core

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.YearMonth
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PickerCoreTest {
    @Test
    fun sampleNowProjectsOneInstantIntoExplicitZone() {
        val instant = Instant.parse("2026-09-05T17:00:00Z")
        val environment = PickerEnvironment(
            clock = Clock.fixed(instant, ZoneOffset.UTC),
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        val now = environment.sampleNow()

        assertEquals(instant, now.instant)
        assertEquals(LocalDateTime.of(2026, 9, 6, 1, 0), now.localDateTime)
        assertEquals(LocalDate.of(2026, 9, 6), now.date)
    }

    @Test
    fun clockZoneDoesNotOverrideExplicitEnvironmentZone() {
        val environment = PickerEnvironment(
            clock = Clock.fixed(Instant.parse("2026-09-05T17:00:00Z"), ZoneOffset.UTC),
            zoneId = ZoneId.of("America/Los_Angeles"),
        )

        assertEquals(
            LocalDateTime.of(2026, 9, 5, 10, 0),
            environment.sampleNow().localDateTime,
        )
    }

    @Test
    fun sampleNowReadsClockExactlyOnce() {
        val clock = CountingClock(
            instant = Instant.parse("2026-09-05T17:00:00Z"),
            zone = ZoneId.of("Asia/Shanghai"),
        )

        clock.environment().sampleNow()

        assertEquals(1, clock.instantCalls)
    }

    @Test
    fun strictCodecsDistinguishIncompleteInvalidAndParsedValues() {
        val date = PickerCodecs.date()
        val time = PickerCodecs.time(TimeFormatOptions(TimePrecision.Second))
        val dateTime = PickerCodecs.dateTime(TimeFormatOptions(TimePrecision.Second))

        assertEquals(ParseResult.Incomplete, date.parse("2026-9-", Locale.CHINA))
        assertEquals(ParseResult.Parsed(LocalDate.of(2026, 9, 6)), date.parse("2026-9-6", Locale.CHINA))
        assertEquals(PickerIssueCodes.InvalidDate, invalidCode(date.parse("2026-02-29", Locale.CHINA)))
        assertEquals(ParseResult.Incomplete, time.parse("1:2:", Locale.CHINA))
        assertEquals(PickerIssueCodes.InvalidTime, invalidCode(time.parse("24:00:00", Locale.CHINA)))
        assertEquals(
            ParseResult.Parsed(LocalDateTime.of(2026, 9, 6, 1, 2, 3)),
            dateTime.parse("2026-9-6T1:2:3", Locale.CHINA),
        )
        assertEquals(ParseResult.Empty, date.parse("   ", Locale.CHINA))
    }

    @Test
    fun textLongerThanThePublicLimitIsRejectedWithoutChangingTheCodecContract() {
        val result = parsePickerText(
            codec = PickerCodecs.date(),
            text = "2".repeat(MAX_PICKER_TEXT_LENGTH + 1),
            locale = Locale.ROOT,
        )

        assertEquals(PickerIssueCodes.TextTooLong, invalidCode(result))
    }

    @Test
    fun defaultDateCodecRejectsUnsupportedYearsAndFormatsExactly() {
        val codec = PickerCodecs.date()

        assertEquals(PickerIssueCodes.InvalidDate, invalidCode(codec.parse("26-01-01", Locale.ROOT)))
        assertEquals(PickerIssueCodes.InvalidDate, invalidCode(codec.parse("0000-01-01", Locale.ROOT)))
        assertEquals(PickerIssueCodes.InvalidFormat, invalidCode(codec.parse("10000-01-01", Locale.ROOT)))
        assertEquals("0001-01-02", codec.format(LocalDate.of(1, 1, 2), Locale.ROOT))
    }

    @Test
    fun h12CodecHandlesNoonMidnightAndChinesePeriods() {
        val options = TimeFormatOptions(TimePrecision.Minute, HourCycle.H12)
        val codec = PickerCodecs.time(options)

        assertEquals(LocalTime.MIDNIGHT, parsed(codec.parse("12:00 AM", Locale.US)))
        assertEquals(LocalTime.NOON, parsed(codec.parse("12:00 PM", Locale.US)))
        assertEquals(LocalTime.of(13, 2), parsed(codec.parse("1:02 下午", Locale.CHINA)))
        assertEquals("01:02 PM", codec.format(LocalTime.of(13, 2), Locale.US))
    }

    @Test
    fun calendarGridFollowsFirstDayAndAlwaysHasSixRows() {
        val instant = Instant.parse("2026-09-01T00:00:00Z")
        val monday = PickerEnvironment(
            clock = Clock.fixed(instant, ZoneOffset.UTC),
            zoneId = ZoneOffset.UTC,
            firstDayOfWeek = DayOfWeek.MONDAY,
        )
        val sunday = monday.copy(firstDayOfWeek = DayOfWeek.SUNDAY)

        val mondayGrid = buildCalendarGrid(YearMonth.of(2026, 9), monday)
        val sundayGrid = buildCalendarGrid(YearMonth.of(2026, 9), sunday)

        assertEquals(CALENDAR_CELL_COUNT, mondayGrid.days.size)
        assertEquals(LocalDate.of(2026, 8, 31), mondayGrid.days.first().date)
        assertEquals(LocalDate.of(2026, 8, 30), sundayGrid.days.first().date)
        assertTrue(mondayGrid.days.filter { it.date != null }.zipWithNext().all { (a, b) ->
            b.date == a.date?.plusDays(1)
        })
    }

    @Test
    fun calendarSupportsLeapYearsAndStrictConstraintOrdering() {
        assertEquals(LocalDate.of(2000, 2, 29), safeDate(2000, 2, 29))
        assertNull(safeDate(1900, 2, 29))
        assertEquals(LocalDate.of(2024, 2, 29), safeDate(2024, 2, 29))
        assertNull(safeDate(2026, 2, 29))
        assertFailsWith<IllegalArgumentException> {
            DateConstraints(
                min = LocalDate.of(2027, 1, 1),
                max = LocalDate.of(2026, 1, 1),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TimeConstraints(
                min = LocalTime.NOON,
                max = LocalTime.MIDNIGHT,
            )
        }
    }

    @Test
    fun precisionMergingPreservesHiddenValuesUntilVisibleTimeChanges() {
        val original = LocalTime.of(1, 2, 3, 456_000_000)
        val sameVisible = LocalTime.of(1, 2, 3)
        val changedSecond = LocalTime.of(1, 2, 4)

        assertEquals(
            original,
            mergeEditedTime(
                original,
                sameVisible,
                TimeFormatOptions(TimePrecision.Second),
                timeWasActuallyChanged = false,
            ),
        )
        assertEquals(
            LocalTime.of(1, 2, 4),
            mergeEditedTime(
                original,
                changedSecond,
                TimeFormatOptions(TimePrecision.Second),
                timeWasActuallyChanged = true,
            ),
        )
        assertEquals(
            LocalTime.of(1, 2),
            mergeEditedTime(
                original,
                LocalTime.of(1, 2, 8),
                TimeFormatOptions(TimePrecision.Minute),
                timeWasActuallyChanged = true,
            ),
        )
    }

    @Test
    fun dstGapIsRejectedAndOverlapCandidatesAreSortedByInstant() {
        val zone = ZoneId.of("America/New_York")
        val gap = resolveInstant(LocalDateTime.of(2024, 3, 10, 2, 30), zone)
        val overlap = resolveInstant(LocalDateTime.of(2024, 11, 3, 1, 30), zone)

        assertIs<InstantResolution.Gap>(gap)
        val candidates = assertIs<InstantResolution.Overlap>(overlap).candidates
        assertEquals(2, candidates.size)
        assertTrue(candidates[0].instant < candidates[1].instant)
        assertEquals(
            candidates[0].instant,
            resolveSelectedInstant(
                LocalDateTime.of(2024, 11, 3, 1, 30),
                zone,
                candidates[0].offset,
            ),
        )
        assertNull(
            resolveSelectedInstant(
                LocalDateTime.of(2024, 11, 3, 1, 30),
                zone,
                ZoneOffset.UTC,
            )
        )
    }

    @Test
    fun nonHourAndSkippedDayTransitionsRemainGaps() {
        assertIs<InstantResolution.Gap>(
            resolveInstant(
                LocalDateTime.of(2026, 10, 4, 2, 15),
                ZoneId.of("Australia/Lord_Howe"),
            ),
        )
        assertIs<InstantResolution.Gap>(
            resolveInstant(
                LocalDateTime.of(2011, 12, 30, 12, 0),
                ZoneId.of("Pacific/Apia"),
            ),
        )
    }

    private fun invalidCode(result: ParseResult<*>): String? =
        (result as? ParseResult.Invalid)?.issue?.code

    private fun <T : Any> parsed(result: ParseResult<T>): T =
        assertIs<ParseResult.Parsed<T>>(result).value

    private class CountingClock(
        private val instant: Instant,
        private val zone: ZoneId,
    ) : Clock() {
        var instantCalls: Int = 0

        override fun instant(): Instant {
            instantCalls += 1
            return instant
        }

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = CountingClock(instant, zone)

        fun environment(): PickerEnvironment = PickerEnvironment(this, zone)
    }
}
