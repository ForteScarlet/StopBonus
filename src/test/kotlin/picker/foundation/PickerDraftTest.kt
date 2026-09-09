package picker.foundation

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.time.LocalTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import picker.core.ParseResult
import picker.core.TimeFormatOptions
import picker.core.TimePrecision

class PickerDraftTest {
    @Test
    fun emptyTimeSegmentsRemainIncompleteInsteadOfBecomingZero() {
        val result = resolveDraft(
            TimeDraft(
                hour = TextFieldValue(),
                minute = TextFieldValue("2", TextRange(1)),
            ),
            TimeFormatOptions(TimePrecision.Minute),
            Locale.ROOT,
        )

        assertEquals(ParseResult.Incomplete, result)
    }

    @Test
    fun draftFactoryUsesCanonicalSegmentsOnlyWhenOpeningAnExistingValue() {
        val draft = TimeDraft.from(
            LocalTime.of(1, 2, 3),
            TimeFormatOptions(TimePrecision.Second),
        )

        assertEquals("01", draft.hour.text)
        assertEquals("02", draft.minute.text)
        assertEquals("03", draft.second.text)
        assertIs<ParseResult.Parsed<LocalTime>>(
            resolveDraft(draft, TimeFormatOptions(TimePrecision.Second), Locale.ROOT),
        )
    }
}
