package picker.foundation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import picker.core.HourCycle
import picker.core.ParseResult
import picker.core.TimeFormatOptions
import picker.core.TimePrecision
import picker.core.hour12To24
import java.time.LocalTime

/**
 * 底层时间编辑器暴露的可编辑分段。
 */
enum class TimeSegment {
    /**
     * 小时分段。
     */
    Hour,
    /**
     * 分钟分段。
     */
    Minute,
    /**
     * 秒分段，仅在秒级精度下可见。
     */
    Second,
}

/**
 * 自定义时间内容读取与修改分段时间草稿所使用的作用域。
 */
class TimeEditorScope internal constructor(
    /**
     * 当前分段草稿。
     */
    val draft: TimeDraft,
    /**
     * 决定可见分段与取值范围的格式规则。
     */
    val format: TimeFormatOptions,
    /**
     * 编辑器是否接受用户操作。
     */
    val enabled: Boolean,
    /**
     * 编辑器是否可见但不可编辑。
     */
    val readOnly: Boolean,
    /**
     * 用于替换草稿的回调。
     */
    val updateDraft: (TimeDraft) -> Unit,
    /**
     * 在不提交的前提下解析当前草稿。
     */
    val resolve: () -> ParseResult<LocalTime>,
) {
    /**
     * 返回 [segment] 的当前文本值。
     */
    fun value(segment: TimeSegment): TextFieldValue = when (segment) {
        TimeSegment.Hour -> draft.hour
        TimeSegment.Minute -> draft.minute
        TimeSegment.Second -> draft.second
    }

    /**
     * 替换可见分段，同时保留光标与输入法状态。
     */
    fun setValue(segment: TimeSegment, value: TextFieldValue) {
        if (!enabled || readOnly || !isVisible(segment)) return
        // 分钟精度下不可间接编辑隐藏秒，避免草稿与编解码契约不一致。
        updateDraft(
            when (segment) {
                TimeSegment.Hour -> draft.copy(hour = value)
                TimeSegment.Minute -> draft.copy(minute = value)
                TimeSegment.Second -> draft.copy(second = value)
            }
        )
    }

    /**
     * 当结果仍在范围内时，将数值分段移动 [amount]。
     */
    fun increment(segment: TimeSegment, amount: Int) {
        val current = value(segment).text.toIntOrNull() ?: return
        val range = rangeOf(segment)
        val next = current + amount
        if (next !in range) return
        setValue(segment, textFieldValue(next.toString()))
    }

    /**
     * 在 [segment] 的合法范围内循环移动数值。
     *
     * 此操作专供滚轮、拖拽和方向键等连续控制使用，不改变 [increment] 的边界停止语义。
     * 空分段向上移动时从最小值开始，向下移动时从最大值开始。
     */
    fun cycle(segment: TimeSegment, amount: Int) {
        if (!isVisible(segment) || amount == 0) return
        val range = rangeOf(segment)
        val current = value(segment).text.toIntOrNull()
        val initial = current ?: if (amount > 0) range.last else range.first
        val next = Math.floorMod(initial - range.first + amount, range.count()) + range.first
        setNumber(segment, next)
    }

    /**
     * 清除 [segment] 的当前数字，使其回到未选择状态。
     */
    fun clearNumber(segment: TimeSegment) {
        if (isVisible(segment)) setValue(segment, TextFieldValue())
    }

    /**
     * 将分段替换为合法数字，供表盘等非文本控件复用同一草稿状态。
     */
    fun setNumber(segment: TimeSegment, number: Int) {
        if (number in rangeOf(segment)) {
            setValue(segment, textFieldValue(number.toString()))
        }
    }

    /**
     * 返回 [segment] 在不越界的前提下能否移动 [amount]。
     */
    fun canIncrement(segment: TimeSegment, amount: Int): Boolean {
        if (!isVisible(segment)) return false
        val current = value(segment).text.toIntOrNull() ?: return false
        return current + amount in rangeOf(segment)
    }

    /**
     * 在 12 小时制下切换或初始化上午/下午。
     */
    fun togglePeriod() {
        if (!enabled || readOnly || format.hourCycle != HourCycle.H12) return
        updateDraft(
            draft.copy(
                period = when (draft.period) {
                    DayPeriod.AM -> DayPeriod.PM
                    DayPeriod.PM -> DayPeriod.AM
                    null -> DayPeriod.AM
                }
            )
        )
    }

    /**
     * 若当前小时可解析，返回其 24 小时制数值。
     */
    fun currentHour24(): Int? {
        val hour = draft.hour.text.toIntOrNull() ?: return null
        return if (format.hourCycle == HourCycle.H12) {
            hour12To24(hour, draft.period == DayPeriod.PM)
        } else {
            hour
        }
    }

    private fun rangeOf(segment: TimeSegment): IntRange = when (segment) {
        TimeSegment.Hour -> if (format.hourCycle == HourCycle.H12) 1..12 else 0..23
        TimeSegment.Minute, TimeSegment.Second -> 0..59
    }

    private fun isVisible(segment: TimeSegment): Boolean =
        segment != TimeSegment.Second || format.precision == TimePrecision.Second
}

/**
 * 提供键盘增减行为的 Foundation 时间编辑器，视觉控件由 [content] 决定。
 */
@Composable
fun BasicTimeEditor(
    /**
     * 当前分段草稿。
     */
    draft: TimeDraft,
    /**
     * 接收已接受的草稿变化。
     */
    onDraftChange: (TimeDraft) -> Unit,
    /**
     * 小时制与可见精度。
     */
    format: TimeFormatOptions,
    /**
     * 应用于编辑器容器的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 控件是否接受输入。
     */
    enabled: Boolean = true,
    /**
     * 控件是否可见但不可编辑。
     */
    readOnly: Boolean = false,
    /**
     * 输入法组合结束后按 Enter 时调用。
     */
    onSubmit: () -> Unit = {},
    /**
     * 在 [TimeEditorScope] 中渲染的自定义控件。
     */
    content: @Composable TimeEditorScope.() -> Unit,
) {
    val scope = TimeEditorScope(
        draft = draft,
        format = format,
        enabled = enabled,
        readOnly = readOnly,
        updateDraft = onDraftChange,
        resolve = { resolveDraft(draft, format) },
    )
    Box(
        modifier = modifier.onPreviewKeyEvent { event ->
            if (
                event.type == KeyEventType.KeyDown &&
                event.key == Key.Enter &&
                enabled &&
                !readOnly &&
                !draft.hasComposition
            ) {
                onSubmit()
                true
            } else {
                false
            }
        },
    ) {
        scope.content()
    }
}

/**
 * 为自定义时间内容渲染单个可编辑数值分段。
 */
@Composable
fun TimeEditorScope.SegmentField(
    /**
     * 此输入框表示的分段。
     */
    segment: TimeSegment,
    /**
     * 应用于分段输入框的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 此分段是否接受输入。
     */
    enabled: Boolean = true,
    /**
     * 此分段是否可见但不可编辑。
     */
    readOnly: Boolean = false,
    /**
     * 分段的无障碍名称。
     */
    label: String = segment.name.lowercase(),
    /**
     * 分段输入框的边框颜色。
     */
    borderColor: Color = Color(0xFF94A3B8),
    /**
     * 分段输入框的文字颜色。
     */
    textColor: Color = Color(0xFF0F172A),
) {
    val fieldValue = value(segment)
    BasicTextField(
        value = fieldValue,
        onValueChange = { setValue(segment, it) },
        modifier = modifier
            .widthIn(min = 36.dp)
            .border(1.dp, borderColor)
            .padding(horizontal = 7.dp, vertical = 5.dp)
            .onPreviewKeyEvent { event ->
                if (!enabled || readOnly || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        if (canIncrement(segment, -1)) {
                            increment(segment, -1)
                            true
                        } else {
                            false
                        }
                    }

                    Key.DirectionDown -> {
                        if (canIncrement(segment, 1)) {
                            increment(segment, 1)
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
            .semantics {
                contentDescription = label
                testTag = PickerSemantics.segment(segment)
                if (!enabled) disabled()
            },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        textStyle = TextStyle(fontSize = 14.sp, color = textColor),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
    )
}

/**
 * 当时间格式采用 12 小时制时渲染上午、下午控件。
 */
@Composable
fun TimeEditorScope.PeriodToggle(
    /**
     * 应用于时段控件的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 时段控件是否接受输入。
     */
    enabled: Boolean = true,
    /**
     * 时段控件是否可见但不可编辑。
     */
    readOnly: Boolean = false,
    /**
     * 已选时段的边框颜色。
     */
    selectedBorderColor: Color = Color(0xFF0F766E),
    /**
     * 未选时段的边框颜色。
     */
    borderColor: Color = Color(0xFF94A3B8),
    /**
     * 已选时段的文字颜色。
     */
    selectedContentColor: Color = Color(0xFF0F766E),
    /**
     * 未选时段的文字颜色。
     */
    contentColor: Color = Color(0xFF0F172A),
) {
    if (format.hourCycle != HourCycle.H12) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(DayPeriod.AM, DayPeriod.PM).forEach { period ->
            Box(
                modifier = Modifier
                    .border(1.dp, if (draft.period == period) selectedBorderColor else borderColor)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clickable(enabled = enabled && !readOnly) {
                        updateDraft(draft.copy(period = period))
                    }
                    .semantics {
                        contentDescription =
                            if (period == DayPeriod.AM) "上午" else "下午"
                        selected = draft.period == period
                        role = Role.Button
                        if (!enabled || readOnly) disabled()
                    },
            ) {
                androidx.compose.foundation.text.BasicText(
                    if (period == DayPeriod.AM) "AM" else "PM",
                    style = TextStyle(color = if (draft.period == period) selectedContentColor else contentColor),
                )
            }
        }
    }
}
