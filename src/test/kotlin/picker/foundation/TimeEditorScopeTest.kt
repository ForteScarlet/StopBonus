package picker.foundation

import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import picker.core.TimeFormatOptions

/**
 * 覆盖时间数字窗所依赖的循环微调与分段清除语义。
 */
class TimeEditorScopeTest {
    @Test
    fun 循环微调在上下边界回绕且保留其他分段() {
        var draft = TimeDraft(
            hour = TextFieldValue("23"),
            minute = TextFieldValue("59"),
        )

        fun scope() = TimeEditorScope(
            draft = draft,
            format = TimeFormatOptions(),
            enabled = true,
            readOnly = false,
            updateDraft = { draft = it },
            resolve = { resolveDraft(draft, TimeFormatOptions()) },
        )

        scope().cycle(TimeSegment.Hour, 1)
        scope().cycle(TimeSegment.Minute, 1)

        assertEquals("0", draft.hour.text)
        assertEquals("0", draft.minute.text)

        scope().cycle(TimeSegment.Hour, -1)
        scope().cycle(TimeSegment.Minute, -1)

        assertEquals("23", draft.hour.text)
        assertEquals("59", draft.minute.text)
    }

    @Test
    fun 空分段按滚动方向选择相应边界并可单独清除() {
        var draft = TimeDraft(
            hour = TextFieldValue("08"),
            minute = TextFieldValue(),
        )

        fun scope() = TimeEditorScope(
            draft = draft,
            format = TimeFormatOptions(),
            enabled = true,
            readOnly = false,
            updateDraft = { draft = it },
            resolve = { resolveDraft(draft, TimeFormatOptions()) },
        )

        scope().cycle(TimeSegment.Minute, -1)
        assertEquals("59", draft.minute.text)

        scope().clearNumber(TimeSegment.Minute)
        assertEquals("", draft.minute.text)
        assertEquals("08", draft.hour.text)
    }
}
