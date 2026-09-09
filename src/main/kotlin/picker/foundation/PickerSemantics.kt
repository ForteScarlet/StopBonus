package picker.foundation

import java.time.LocalDate

internal object PickerSemantics {
    const val FIELD: String = "picker-field"
    const val POPUP: String = "picker-popup"

    fun day(date: LocalDate): String = "picker-day-$date"

    fun segment(segment: TimeSegment): String = when (segment) {
        TimeSegment.Hour -> "picker-hour"
        TimeSegment.Minute -> "picker-minute"
        TimeSegment.Second -> "picker-second"
    }

    fun offset(index: Int): String = "picker-offset-$index"
}
