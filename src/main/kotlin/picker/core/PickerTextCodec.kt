package picker.core

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * 用户文本的解析结果，明确区分空值、未完成和无效输入。
 */
sealed interface ParseResult<out T> {
    /**
     * 字段不含文本。
     */
    data object Empty : ParseResult<Nothing>

    /**
     * 文本是有效前缀，但尚未构成完整值。
     */
    data object Incomplete : ParseResult<Nothing>

    /**
     * 完整文本已解析为 [value]。
     */
    data class Parsed<T>(
        /**
         * 编解码器领域类型的解析值。
         */
        val value: T,
    ) : ParseResult<T>

    /**
     * 解析失败，并返回稳定的选择器问题。
     */
    data class Invalid(
        /**
         * 文本无法接受的原因。
         */
        val issue: PickerIssue,
    ) : ParseResult<Nothing>
}

/**
 * 严格且感知区域设置的选择器值文本编解码器。
 *
 * 实现不得修改调用方文本；可规范化私有解析副本，字段仍保留原文供用户修正。
 */
interface PickerTextCodec<T : Any> {
    /**
     * 输入为空时展示的简短格式提示。
     */
    val formatHint: String

    /**
     * 将文本解析为明确的空值、未完成、已解析或无效状态。
     */
    fun parse(text: String, locale: Locale): ParseResult<T>

    /**
     * 将值格式化为选择器规范文本。
     */
    fun format(value: T, locale: Locale): String
}

/**
 * 执行通用长度检查后委托给编解码器。
 */
fun <T : Any> parsePickerText(
    /**
     * 定义可接受语法的编解码器。
     */
    codec: PickerTextCodec<T>,
    /**
     * 待解析文本；此函数不会改写原值。
     */
    text: String,
    /**
     * 编解码器使用的区域设置。
     */
    locale: Locale,
): ParseResult<T> = if (text.length > MAX_PICKER_TEXT_LENGTH) {
    ParseResult.Invalid(PickerIssue(PickerIssueCodes.TextTooLong))
} else {
    codec.parse(text, locale)
}

/**
 * 选择器支持的本地日期时间类型内置编解码器。
 */
object PickerCodecs {
    /**
     * 返回严格的 `YYYY-MM-DD` 本地日期编解码器。
     */
    fun date(): PickerTextCodec<LocalDate> = DateCodec

    /**
     * 返回使用 [options] 的本地时间编解码器。
     */
    fun time(options: TimeFormatOptions = TimeFormatOptions()): PickerTextCodec<LocalTime> =
        TimeCodec(options)

    /**
     * 返回使用 [options] 的本地日期时间编解码器。
     */
    fun dateTime(options: TimeFormatOptions = TimeFormatOptions()): PickerTextCodec<LocalDateTime> =
        DateTimeCodec(options)
}

private object DateCodec : PickerTextCodec<LocalDate> {
    private val complete = Regex("""(\d{1,4})-(\d{1,2})-(\d{1,2})""")
    private val prefix = Regex("""\d{1,4}-(?:\d{0,2}(?:-\d{0,2})?)?""")

    override val formatHint: String = "YYYY-MM-DD"

    override fun parse(text: String, locale: Locale): ParseResult<LocalDate> = parseNormalized(text) { normalized ->
        // 仅规范化私有解析副本，TextFieldValue 仍保留用户输入的原文。
        val match = complete.matchEntire(normalized)
            ?: return if (prefix.matches(normalized)) {
                ParseResult.Incomplete
            } else {
                invalid(PickerIssueCodes.InvalidFormat)
            }

        val yearText = match.groupValues[1]
        if (yearText.length != 4) return invalid(PickerIssueCodes.InvalidDate)

        val year = yearText.toIntOrNull() ?: return invalid(PickerIssueCodes.InvalidDate)
        val month = match.groupValues[2].toIntOrNull() ?: return invalid(PickerIssueCodes.InvalidDate)
        val day = match.groupValues[3].toIntOrNull() ?: return invalid(PickerIssueCodes.InvalidDate)

        return try {
            if (year !in PICKER_MIN_YEAR..PICKER_MAX_YEAR) {
                invalid(PickerIssueCodes.InvalidDate)
            } else {
                ParseResult.Parsed(LocalDate.of(year, month, day))
            }
        } catch (_: DateTimeException) {
            invalid(PickerIssueCodes.InvalidDate)
        }
    }

    override fun format(value: LocalDate, locale: Locale): String =
        "%04d-%02d-%02d".format(Locale.ROOT, value.year, value.monthValue, value.dayOfMonth)
}

private data class TimeCodec(
    private val options: TimeFormatOptions,
) : PickerTextCodec<LocalTime> {
    private val complete = Regex(
        pattern = """(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?""",
    )
    private val withPeriod = Regex(
        pattern = """(?i)(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?\s*(AM|PM|上午|下午)""",
    )
    private val timePrefix = Regex("""\d{0,2}|\d{1,2}:\d{0,2}|\d{1,2}:\d{1,2}:\d{0,2}""")
    private val periodPrefix = Regex("""(?i)\d{1,2}:\d{1,2}(?::\d{1,2})?\s*[APM上下午]*""")

    override val formatHint: String
        get() = when (options.hourCycle) {
            HourCycle.H24 -> if (options.precision == TimePrecision.Second) "HH:MM:SS" else "HH:MM"
            HourCycle.H12 -> if (options.precision == TimePrecision.Second) "HH:MM:SS AM" else "HH:MM AM"
        }

    override fun parse(text: String, locale: Locale): ParseResult<LocalTime> = parseNormalized(text) { normalized ->
        // 十二与二十四小时制使用独立语法，避免忽略时段或让分钟精度接收隐藏秒值。
        when (options.hourCycle) {
            HourCycle.H24 -> parse24(normalized)
            HourCycle.H12 -> parse12(normalized)
        }
    }

    override fun format(value: LocalTime, locale: Locale): String {
        val hour = when (options.hourCycle) {
            HourCycle.H24 -> value.hour
            HourCycle.H12 -> ((value.hour + 11) % 12) + 1
        }
        val suffix = when (options.hourCycle) {
            HourCycle.H24 -> ""
            HourCycle.H12 -> if (value.hour < 12) " AM" else " PM"
        }
        val base = if (options.precision == TimePrecision.Second) {
            "%02d:%02d:%02d".format(Locale.ROOT, hour, value.minute, value.second)
        } else {
            "%02d:%02d".format(Locale.ROOT, hour, value.minute)
        }
        return base + suffix
    }

    private fun parse24(text: String): ParseResult<LocalTime> {
        val match = complete.matchEntire(text)
            ?: return incompleteOrInvalid(looksLikeTimePrefix(text))
        return parseMatchedTime(match)
    }

    private fun parse12(text: String): ParseResult<LocalTime> {
        val match = withPeriod.matchEntire(text)
            ?: return incompleteOrInvalid(looksLikeTimePrefix(text) || looksLikePeriodPrefix(text))
        val period = match.groupValues[4].uppercase(Locale.ROOT)
        val isPm = period == "PM" || period == "下午"
        return parseMatchedTime(match, validHours = 1..12) { hour ->
            (hour % 12) + if (isPm) 12 else 0
        }
    }

    private fun parseMatchedTime(
        match: MatchResult,
        validHours: IntRange = 0..23,
        transformHour: (Int) -> Int = { it },
    ): ParseResult<LocalTime> {
        if (match.groupValues[3].isNotEmpty() && options.precision == TimePrecision.Minute) {
            return invalid(PickerIssueCodes.InvalidFormat)
        }
        val hour = match.groupValues[1].toIntOrNull() ?: return invalid(PickerIssueCodes.InvalidTime)
        val minute = match.groupValues[2].toIntOrNull() ?: return invalid(PickerIssueCodes.InvalidTime)
        val second = match.groupValues[3].ifEmpty { "0" }.toIntOrNull()
            ?: return invalid(PickerIssueCodes.InvalidTime)
        if (hour !in validHours) return invalid(PickerIssueCodes.InvalidTime)
        return strictTime(transformHour(hour), minute, second)
    }

    private fun incompleteOrInvalid(incomplete: Boolean): ParseResult<LocalTime> =
        if (incomplete) ParseResult.Incomplete else invalid(PickerIssueCodes.InvalidFormat)

    private fun strictTime(hour: Int, minute: Int, second: Int): ParseResult<LocalTime> =
        try {
            ParseResult.Parsed(LocalTime.of(hour, minute, second))
        } catch (_: DateTimeException) {
            invalid(PickerIssueCodes.InvalidTime)
        }

    private fun looksLikeTimePrefix(text: String): Boolean = timePrefix.matches(text)

    private fun looksLikePeriodPrefix(text: String): Boolean = periodPrefix.matches(text)
}

private data class DateTimeCodec(
    private val options: TimeFormatOptions,
) : PickerTextCodec<LocalDateTime> {
    private val timeCodec = TimeCodec(options)

    override val formatHint: String
        get() = DateCodec.formatHint + " " + timeCodec.formatHint

    override fun parse(text: String, locale: Locale): ParseResult<LocalDateTime> = parseNormalized(text) { normalized ->
        val separatorIndex = normalized.indexOfFirst { it == 'T' || it == 't' || it == ' ' }
        if (separatorIndex < 0) {
            return if (DateCodec.parse(normalized, locale) is ParseResult.Incomplete) {
                ParseResult.Incomplete
            } else {
                invalid(PickerIssueCodes.InvalidFormat)
            }
        }

        val dateText = normalized.substring(0, separatorIndex)
        val timeText = normalized.substring(separatorIndex).trimStart(' ', 'T', 't')
        if (dateText.isEmpty() || timeText.isEmpty()) return ParseResult.Incomplete

        // 分别解析日期和时间，使调用方只需修正未完成或无效的部分。
        val dateResult = DateCodec.parse(dateText, locale)
        val timeResult = timeCodec.parse(timeText, locale)
        if (dateResult !is ParseResult.Parsed || timeResult !is ParseResult.Parsed) {
            return when {
                dateResult is ParseResult.Invalid -> dateResult
                timeResult is ParseResult.Invalid -> timeResult
                else -> ParseResult.Incomplete
            }
        }
        return ParseResult.Parsed(
            LocalDateTime.of(dateResult.value, timeResult.value),
        )
    }

    override fun format(value: LocalDateTime, locale: Locale): String =
        DateCodec.format(value.toLocalDate(), locale) + " " +
                timeCodec.format(value.toLocalTime(), locale)
}

private inline fun <T> parseNormalized(
    text: String,
    parse: (String) -> ParseResult<T>,
): ParseResult<T> {
    val normalized = normalizeInput(text)
    if (normalized.length > MAX_PICKER_TEXT_LENGTH) return tooLong()
    if (normalized.isEmpty()) return ParseResult.Empty
    return parse(normalized)
}

private fun normalizeInput(text: String): String =
    buildString(text.length) {
        text.trim().forEach { char ->
            append(
                when (char) {
                    in '０'..'９' -> ('0'.code + (char.code - '０'.code)).toChar()
                    '：' -> ':'
                    '－' -> '-'
                    else -> char
                }
            )
        }
    }

private fun tooLong(): ParseResult.Invalid =
    invalid(PickerIssueCodes.TextTooLong)

private fun invalid(code: String): ParseResult.Invalid =
    ParseResult.Invalid(PickerIssue(code))
