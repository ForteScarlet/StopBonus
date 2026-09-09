package picker.foundation

import androidx.compose.ui.text.input.TextFieldValue
import picker.core.HourCycle
import picker.core.ParseResult
import picker.core.PickerCodecs
import picker.core.PickerIssue
import picker.core.PickerIssueCodes
import picker.core.PickerTextCodec
import picker.core.TimeFormatOptions
import picker.core.TimePrecision
import picker.core.parsePickerText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * 十二小时制时间草稿使用的时段选项。
 */
enum class DayPeriod {
    /**
     * 上午，显示为 AM 或“上午”。
     */
    AM,

    /**
     * 下午，显示为 PM 或“下午”。
     */
    PM,
}

/**
 * 由 [TextFieldValue] 承载的可编辑时间分段，使选区和输入法组合状态在更新时得以保留。
 */
data class TimeDraft(
    /**
     * 小时分段，取值范围由十二或二十四小时制决定。
     */
    val hour: TextFieldValue = TextFieldValue(),
    /**
     * 分钟分段。
     */
    val minute: TextFieldValue = TextFieldValue(),
    /**
     * 秒分段；使用分钟精度时忽略。
     */
    val second: TextFieldValue = TextFieldValue(),
    /**
     * 十二小时制选中的上午或下午时段。
     */
    val period: DayPeriod? = null,
    /**
     * 标记一次需要重置原隐藏值的精度切换。
     */
    val forcePrecisionReset: Boolean = false,
) {
    companion object {
        /**
         * 按指定小时制与精度，从本地时间创建使用规范分段文本的可编辑草稿。
         */
        fun from(
            value: LocalTime?,
            format: TimeFormatOptions = TimeFormatOptions(),
        ): TimeDraft {
            if (value == null) return TimeDraft()
            val hour = when (format.hourCycle) {
                HourCycle.H24 -> value.hour
                HourCycle.H12 -> ((value.hour + 11) % 12) + 1
            }
            return TimeDraft(
                hour = textFieldValue("%02d".format(Locale.ROOT, hour)),
                minute = textFieldValue("%02d".format(Locale.ROOT, value.minute)),
                second = if (format.precision == TimePrecision.Second) {
                    textFieldValue("%02d".format(Locale.ROOT, value.second))
                } else {
                    TextFieldValue()
                },
                period = if (format.hourCycle == HourCycle.H12) {
                    if (value.hour < 12) DayPeriod.AM else DayPeriod.PM
                } else {
                    null
                },
            )
        }
    }
}

/**
 * 以单个 [TextFieldValue] 保留的可编辑日期文本。
 */
data class DateDraft(
    /**
     * 原始日期输入，包含光标与输入法组合状态。
     */
    val input: TextFieldValue = TextFieldValue(),
) {
    companion object {
        /**
         * 使用给定编解码器与语言环境从值创建日期草稿。
         */
        fun from(
            value: LocalDate?,
            codec: PickerTextCodec<LocalDate> = PickerCodecs.date(),
            locale: Locale = Locale.SIMPLIFIED_CHINESE,
        ): DateDraft =
            DateDraft(
                input = value?.let { textFieldValue(codec.format(it, locale)) } ?: TextFieldValue(),
            )
    }
}

/**
 * 本地日期与本地时间组成的可编辑草稿。
 */
data class DateTimeDraft(
    /**
     * 可编辑的本地日期部分。
     */
    val date: DateDraft = DateDraft(),
    /**
     * 可编辑的本地时间部分。
     */
    val time: TimeDraft = TimeDraft(),
) {
    companion object {
        /**
         * 从本地日期时间值创建组合草稿。
         */
        fun from(
            value: LocalDateTime?,
            format: TimeFormatOptions = TimeFormatOptions(),
            locale: Locale = Locale.SIMPLIFIED_CHINESE,
        ): DateTimeDraft =
            DateTimeDraft(
                date = DateDraft.from(value?.toLocalDate(), PickerCodecs.date(), locale),
                time = TimeDraft.from(value?.toLocalTime(), format),
            )
    }
}

/**
 * 使用给定的严格日期编解码器解析日期草稿。
 */
fun resolveDraft(
    /**
     * 待解析原始文本的草稿。
     */
    draft: DateDraft,
    /**
     * 定义语法与格式提示的编解码器。
     */
    codec: PickerTextCodec<LocalDate> = PickerCodecs.date(),
    /**
     * 编解码器使用的语言环境。
     */
    locale: Locale = Locale.SIMPLIFIED_CHINESE,
): ParseResult<LocalDate> = parsePickerText(codec, draft.input.text, locale)

/**
 * 解析分段时间草稿，并保留未完成与输入法组合状态。
 */
fun resolveDraft(
    /**
     * 可编辑时间分段。
     */
    draft: TimeDraft,
    /**
     * 用于解释分段的小时制与可见精度。
     */
    format: TimeFormatOptions = TimeFormatOptions(),
    /**
     * 严格时间编解码器使用的语言环境。
     */
    locale: Locale = Locale.SIMPLIFIED_CHINESE,
): ParseResult<LocalTime> {
    if (draft.hasComposition) return ParseResult.Incomplete
    val isEmpty = draft.hour.text.isEmpty() &&
            draft.minute.text.isEmpty() &&
            draft.second.text.isEmpty() &&
            draft.period == null
    if (isEmpty) return ParseResult.Empty
    if (format.precision == TimePrecision.Minute && draft.second.text.isNotEmpty()) {
        return ParseResult.Invalid(PickerIssue(PickerIssueCodes.InvalidFormat))
    }
    if (format.hourCycle == HourCycle.H24 && draft.period != null) {
        return ParseResult.Invalid(PickerIssue(PickerIssueCodes.InvalidFormat))
    }
    if (
        draft.hour.text.isEmpty() ||
        draft.minute.text.isEmpty() ||
        (format.precision == TimePrecision.Second && draft.second.text.isEmpty()) ||
        (format.hourCycle == HourCycle.H12 && draft.period == null)
    ) {
        return ParseResult.Incomplete
    }
    val text = draft.toInputText(format)
    return parsePickerText(PickerCodecs.time(format), text, locale)
}

/**
 * 把日期时间草稿的两部分解析为一个本地日期时间值。
 */
fun resolveDraft(
    /**
     * 可编辑的组合日期时间草稿。
     */
    draft: DateTimeDraft,
    /**
     * 时间部分的格式规则。
     */
    format: TimeFormatOptions = TimeFormatOptions(),
    /**
     * 两个编解码器共用的语言环境。
     */
    locale: Locale = Locale.SIMPLIFIED_CHINESE,
): ParseResult<LocalDateTime> {
    if (draft.hasComposition) return ParseResult.Incomplete
    val dateResult = resolveDraft(draft.date, PickerCodecs.date(), locale)
    val timeResult = resolveDraft(draft.time, format, locale)
    return when {
        dateResult is ParseResult.Invalid -> dateResult
        timeResult is ParseResult.Invalid -> timeResult
        dateResult is ParseResult.Parsed && timeResult is ParseResult.Parsed ->
            ParseResult.Parsed(LocalDateTime.of(dateResult.value, timeResult.value))

        dateResult is ParseResult.Empty && timeResult is ParseResult.Empty -> ParseResult.Empty
        else -> ParseResult.Incomplete
    }
}

/**
 * 任一时间分段正在进行输入法组合时返回 true。
 */
internal val TimeDraft.hasComposition: Boolean
    get() = listOf(hour, minute, second).any { it.composition != null }

/**
 * 日期或时间部分正在进行输入法组合时返回 true。
 */
internal val DateTimeDraft.hasComposition: Boolean
    get() = date.input.composition != null || time.hasComposition

/**
 * 将分段时间文本拼接为严格编解码器所需的语法。
 */
internal fun TimeDraft.toInputText(format: TimeFormatOptions): String {
    val hourText = hour.text
    val minuteText = minute.text
    val secondText = second.text
    if (hourText.isEmpty() && minuteText.isEmpty() && secondText.isEmpty() && period == null) {
        return ""
    }
    val base = buildString {
        append(hourText)
        append(':')
        append(minuteText)
        if (format.precision == TimePrecision.Second) {
            append(':')
            append(secondText)
        }
    }
    return if (format.hourCycle == HourCycle.H12) {
        val suffix = when (period) {
            DayPeriod.AM -> " AM"
            DayPeriod.PM -> " PM"
            null -> ""
        }
        base + suffix
    } else {
        base
    }
}

/**
 * 创建光标位于给定规范文本末尾的值。
 */
internal fun textFieldValue(text: String): TextFieldValue =
    TextFieldValue(text = text, selection = androidx.compose.ui.text.TextRange(text.length))
