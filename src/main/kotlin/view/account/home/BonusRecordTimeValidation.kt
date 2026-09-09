package view.account.home

import picker.core.PickerEnvironment
import picker.core.PickerIssue
import picker.core.PickerIssueCodes
import picker.core.PickerNow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Earliest local calendar date accepted for a bonus record.
 */
val EARLIEST_RECORD_DATE: LocalDate = LocalDate.of(1900, 1, 1)

/**
 * Identifies which record-time field failed validation.
 */
enum class RecordTimeField {
    /**
     * Start instant.
     */
    Start,
    /**
     * End instant.
     */
    End,
}

/**
 * Validated immutable instants and the duration between them.
 */
data class ValidatedRecordTimes(
    /**
     * Validated start instant.
     */
    val start: Instant,
    /**
     * Validated end instant.
     */
    val end: Instant,
    /**
     * Duration computed from [start] to [end].
     */
    val duration: Duration,
)

/**
 * Outcome of validating the two instants used by a bonus record.
 */
sealed interface RecordTimesResult {
    /**
     * Both instants pass local-date, future, and ordering rules.
     */
    data class Valid(
        /**
         * Immutable validated value bundle.
         */
        val times: ValidatedRecordTimes,
    ) : RecordTimesResult {
        /**
         * Validated start instant.
         */
        val start: Instant
            get() = times.start
        /**
         * Validated end instant.
         */
        val end: Instant
            get() = times.end
        /**
         * Validated duration.
         */
        val duration: Duration
            get() = times.duration
    }

    /**
     * One field failed validation and should receive the issue.
     */
    data class Invalid(
        /**
         * Field that should display the issue.
         */
        val field: RecordTimeField,
        /**
         * Stable picker issue describing the failure.
         */
        val issue: PickerIssue,
    ) : RecordTimesResult
}

/**
 * Validates record instants in business order and computes their absolute
 * duration without mutating caller state.
 */
fun validateRecordTimes(
    /**
     * Candidate start instant.
     */
    start: Instant?,
    /**
     * Candidate end instant.
     */
    end: Instant?,
    /**
     * Environment used to derive local record dates.
     */
    environment: PickerEnvironment,
    /**
     * One consistent current-time snapshot for future checks.
     */
    now: PickerNow,
): RecordTimesResult {
    if (start == null) {
        return RecordTimesResult.Invalid(
            field = RecordTimeField.Start,
            issue = PickerIssue(PickerIssueCodes.Required),
        )
    }
    if (end == null) {
        return RecordTimesResult.Invalid(
            field = RecordTimeField.End,
            issue = PickerIssue(PickerIssueCodes.Required),
        )
    }

    // The lower business bound is a local-calendar rule, while future and
    // ordering checks remain absolute-instant rules.
    val startDate = start.atZone(environment.zoneId).toLocalDate()
    if (startDate < EARLIEST_RECORD_DATE) {
        return RecordTimesResult.Invalid(
            field = RecordTimeField.Start,
            issue = PickerIssue(
                code = PickerIssueCodes.BeforeMin,
                arguments = mapOf("min" to EARLIEST_RECORD_DATE.toString()),
            ),
        )
    }
    if (start > now.instant) {
        return RecordTimesResult.Invalid(
            field = RecordTimeField.Start,
            issue = PickerIssue(
                code = PickerIssueCodes.AfterMax,
                arguments = mapOf("max" to now.instant.toString()),
            ),
        )
    }

    val endDate = end.atZone(environment.zoneId).toLocalDate()
    if (endDate < EARLIEST_RECORD_DATE) {
        return RecordTimesResult.Invalid(
            field = RecordTimeField.End,
            issue = PickerIssue(
                code = PickerIssueCodes.BeforeMin,
                arguments = mapOf("min" to EARLIEST_RECORD_DATE.toString()),
            ),
        )
    }
    if (end > now.instant) {
        return RecordTimesResult.Invalid(
            field = RecordTimeField.End,
            issue = PickerIssue(
                code = PickerIssueCodes.AfterMax,
                arguments = mapOf("max" to now.instant.toString()),
            ),
        )
    }
    if (end <= start) {
        return RecordTimesResult.Invalid(
            field = RecordTimeField.End,
            issue = PickerIssue(PickerIssueCodes.EndBeforeStart),
        )
    }

    return RecordTimesResult.Valid(
        times = ValidatedRecordTimes(
            start = start,
            end = end,
            duration = Duration.between(start, end),
        ),
    )
}
