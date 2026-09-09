package picker.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

/**
 * 编解码器、校验器和界面状态共享的稳定问题标识。
 */
object PickerIssueCodes {
    /**
     * 必填字段未提供值。
     */
    const val Required: String = "required"

    /**
     * 输入结构有效，但仍有一个或多个分段未完成。
     */
    const val Incomplete: String = "incomplete"

    /**
     * 完整输入不符合所选编解码器格式。
     */
    const val InvalidFormat: String = "invalid_format"

    /**
     * 日期部分无法表示真实日历日期。
     */
    const val InvalidDate: String = "invalid_date"

    /**
     * 时间部分无法表示真实墙上时间。
     */
    const val InvalidTime: String = "invalid_time"

    /**
     * 值早于配置的最小值。
     */
    const val BeforeMin: String = "before_min"

    /**
     * 值晚于配置的最大值。
     */
    const val AfterMax: String = "after_max"

    /**
     * 值本身有效，但被附加校验器拒绝。
     */
    const val NotSelectable: String = "not_selectable"

    /**
     * 本地值落入时区缺口。
     */
    const val DstGap: String = "dst_gap"

    /**
     * 存在时间重叠，但尚未选择偏移量。
     */
    const val OffsetRequired: String = "offset_required"

    /**
     * 用户编辑期间外部值发生变化。
     */
    const val ExternalConflict: String = "external_conflict"

    /**
     * 编辑时刻字段期间时区发生变化。
     */
    const val ZoneConflict: String = "zone_conflict"

    /**
     * 编辑期间可见时间精度发生变化。
     */
    const val PrecisionConflict: String = "precision_conflict"

    /**
     * 外部持有方未接受请求提交的候选值。
     */
    const val CommitNotAccepted: String = "commit_not_accepted"

    /**
     * 值超出选择器支持的年份或数值范围。
     */
    const val ValueOutOfRange: String = "value_out_of_range"

    /**
     * 原始输入超过 [MAX_PICKER_TEXT_LENGTH]。
     */
    const val TextTooLong: String = "text_too_long"

    /**
     * 结束时刻不晚于开始时刻。
     */
    const val EndBeforeStart: String = "end_before_start"

    /**
     * 当前状态尚不能执行请求的操作。
     */
    const val NotReady: String = "not_ready"

}

/**
 * 稳定的校验问题及渲染消息所需的值。
 */
data class PickerIssue(
    /**
     * 来自 [PickerIssueCodes] 或应用扩展的稳定标识。
     */
    val code: String,
    /**
     * 本地化消息占位符的具名替换值。
     */
    val arguments: Map<String, String> = emptyMap(),
)

/**
 * 选择器值的校验结果。
 */
sealed interface ValidationResult {
    /**
     * 值满足全部配置规则。
     */
    data object Valid : ValidationResult

    /**
     * 值被拒绝，并提供一个面向用户的问题。
     */
    data class Invalid(
        /**
         * 值无效的原因。
         */
        val issue: PickerIssue,
    ) : ValidationResult
}

/**
 * 在内置约束之后执行应用特定校验。
 */
fun interface ValueValidator<T : Any> {
    /**
     * 根据当前 [now] 快照校验 [value]。
     */
    fun validate(value: T, now: PickerNow): ValidationResult
}

/**
 * 闭区间日期范围及可选的应用特定日期校验。
 */
data class DateConstraints(
    /**
     * 最早可选日期。
     */
    val min: LocalDate = LocalDate.of(PICKER_MIN_YEAR, 1, 1),
    /**
     * 最晚可选日期。
     */
    val max: LocalDate = LocalDate.of(PICKER_MAX_YEAR, 12, 31),
    /**
     * 通过 [min] 与 [max] 检查后执行的附加规则。
     */
    val additional: ValueValidator<LocalDate>? = null,
) {
    init {
        require(min <= max) {
            "DateConstraints.min ($min) must not be after max ($max)"
        }
    }
}

/**
 * 闭区间墙上时间范围及可选的应用特定时间校验。
 */
data class TimeConstraints(
    /**
     * 最早可选本地时间。
     */
    val min: LocalTime = LocalTime.MIN,
    /**
     * 最晚可选本地时间。
     */
    val max: LocalTime = LocalTime.MAX,
    /**
     * 通过 [min] 与 [max] 检查后执行的附加规则。
     */
    val additional: ValueValidator<LocalTime>? = null,
) {
    init {
        require(min <= max) {
            "TimeConstraints.min ($min) must not be after max ($max)"
        }
    }
}

/**
 * 先分别应用日期与时间约束，再校验完整值。
 */
data class DateTimeConstraints(
    /**
     * 本地日期部分的约束。
     */
    val dates: DateConstraints = DateConstraints(),
    /**
     * 本地时间部分的约束。
     */
    val times: TimeConstraints = TimeConstraints(),
    /**
     * 日期与时间部分均通过后执行的附加规则。
     */
    val additional: ValueValidator<LocalDateTime>? = null,
)

/**
 * 闭区间绝对时刻范围及可选的应用特定校验。
 */
data class InstantConstraints(
    /**
     * 最早可选时刻；无下界时为 `null`。
     */
    val min: Instant? = null,
    /**
     * 最晚可选时刻；无上界时为 `null`。
     */
    val max: Instant? = null,
    /**
     * 通过 [min] 与 [max] 检查后执行的附加规则。
     */
    val additional: ValueValidator<Instant>? = null,
) {
    init {
        require(min == null || max == null || min <= max) {
            "InstantConstraints.min ($min) must not be after max ($max)"
        }
    }
}

/**
 * 根据支持的年份范围、给定范围及附加应用规则校验本地日期。
 */
fun validateDate(
    /**
     * 待校验日期。
     */
    value: LocalDate,
    /**
     * 内置与应用特定的闭区间日期约束。
     */
    constraints: DateConstraints,
    /**
     * 动态规则使用的一致当前时间快照。
     */
    now: PickerNow,
): ValidationResult {
    if (value.year !in PICKER_MIN_YEAR..PICKER_MAX_YEAR) {
        return ValidationResult.Invalid(
            PickerIssue(
                PickerIssueCodes.ValueOutOfRange,
                mapOf("year" to value.year.toString()),
            )
        )
    }
    validateRange(value, constraints.min, constraints.max)?.let { return it }
    return constraints.additional?.validate(value, now) ?: ValidationResult.Valid
}

/**
 * 根据给定范围及附加规则校验本地时间。
 */
fun validateTime(
    /**
     * 待校验时间。
     */
    value: LocalTime,
    /**
     * 内置与应用特定的闭区间时间约束。
     */
    constraints: TimeConstraints,
    /**
     * 动态规则使用的一致当前时间快照。
     */
    now: PickerNow,
): ValidationResult {
    validateRange(value, constraints.min, constraints.max)?.let { return it }
    return constraints.additional?.validate(value, now) ?: ValidationResult.Valid
}

/**
 * 先校验日期与时间部分，再应用组合规则。
 */
fun validateDateTime(
    /**
     * 待校验本地日期时间。
     */
    value: LocalDateTime,
    /**
     * 日期、时间部分及组合值的约束。
     */
    constraints: DateTimeConstraints,
    /**
     * 动态规则使用的一致当前时间快照。
     */
    now: PickerNow,
): ValidationResult {
    val dateResult = validateDate(value.toLocalDate(), constraints.dates, now)
    if (dateResult !is ValidationResult.Valid) return dateResult

    val timeResult = validateTime(value.toLocalTime(), constraints.times, now)
    if (timeResult !is ValidationResult.Valid) return timeResult

    return constraints.additional?.validate(value, now) ?: ValidationResult.Valid
}

/**
 * 根据给定范围及附加规则校验绝对时刻。
 */
fun validateInstant(
    /**
     * 待校验时刻。
     */
    value: Instant,
    /**
     * 绝对时刻闭区间及可选的应用特定规则。
     */
    constraints: InstantConstraints,
    /**
     * 动态规则使用的一致当前时间快照。
     */
    now: PickerNow,
): ValidationResult {
    validateRange(value, constraints.min, constraints.max)?.let { return it }
    return constraints.additional?.validate(value, now) ?: ValidationResult.Valid
}

private fun <T : Comparable<T>> validateRange(
    value: T,
    min: T?,
    max: T?,
): ValidationResult.Invalid? = when {
    min != null && value < min -> ValidationResult.Invalid(
        PickerIssue(
            PickerIssueCodes.BeforeMin,
            mapOf("min" to min.toString()),
        )
    )

    max != null && value > max -> ValidationResult.Invalid(
        PickerIssue(
            PickerIssueCodes.AfterMax,
            mapOf("max" to max.toString()),
        )
    )

    else -> null
}

/**
 * 标准校验结果对应的本地化选择器消息与标签。
 */
data class PickerStrings(
    /**
     * 缺少必填值时的消息。
     */
    val required: String = "此项为必填项",
    /**
     * 输入仍有未完成分段时的消息。
     */
    val incomplete: String = "请输入完整的值",
    /**
     * 文本不符合编解码器格式时的消息。
     */
    val invalidFormat: String = "格式不正确",
    /**
     * 日历日期不存在时的消息。
     */
    val invalidDate: String = "日期不存在",
    /**
     * 墙上时间无效时的消息。
     */
    val invalidTime: String = "时间不正确",
    /**
     * 值小于配置最小值时的消息。
     */
    val beforeMin: String = "不能早于允许的最小值",
    /**
     * 值大于配置最大值时的消息。
     */
    val afterMax: String = "不能晚于允许的最大值",
    /**
     * 值被选择规则拒绝时的消息。
     */
    val notSelectable: String = "此值不可选择",
    /**
     * 本地时间落入夏令时缺口时的消息。
     */
    val dstGap: String = "该时区此时刻不存在，请修改时间",
    /**
     * 提示用户选择重叠偏移量的消息。
     */
    val offsetRequired: String = "该时间有两个可能的时刻，请选择偏移",
    /**
     * 外部值并发变化时的消息。
     */
    val externalConflict: String = "值已在其他位置更新，请选择如何处理",
    /**
     * 编辑期间时区变化时的消息。
     */
    val zoneConflict: String = "时区已变化，请确认如何继续编辑",
    /**
     * 编辑期间可见时间精度变化时的消息。
     */
    val precisionConflict: String = "显示精度已变化，请确认如何处理输入",
    /**
     * 外部提交回调拒绝修改时的消息。
     */
    val commitNotAccepted: String = "外部值没有接纳这次修改",
    /**
     * 载入最新外部值的操作标签。
     */
    val loadExternal: String = "载入最新值",
    /**
     * 冲突后保留本地草稿的操作标签。
     */
    val keepEditing: String = "保留我的编辑",
    /**
     * 值超出选择器支持范围时的消息。
     */
    val valueOutOfRange: String = "超出选择器支持范围",
    /**
     * 输入超过最大文本长度时的消息。
     */
    val textTooLong: String = "输入内容过长",
    /**
     * 结束时间不晚于开始时间时的消息。
     */
    val endBeforeStart: String = "结束时间必须晚于开始时间",
    /**
     * 字段当前无法提交时的消息。
     */
    val notReady: String = "请先完成输入",
    /**
     * 提示将保留隐藏的秒或纳秒。
     */
    val hiddenPrecision: String = "原值含有未显示的精度；未修改时间时会保留",
) {
    /**
     * 将问题代码及其具名参数解析为展示文本。
     */
    fun message(issue: PickerIssue): String {
        val base = when (issue.code) {
            PickerIssueCodes.Required -> required
            PickerIssueCodes.Incomplete -> incomplete
            PickerIssueCodes.InvalidFormat -> invalidFormat
            PickerIssueCodes.InvalidDate -> invalidDate
            PickerIssueCodes.InvalidTime -> invalidTime
            PickerIssueCodes.BeforeMin -> beforeMin
            PickerIssueCodes.AfterMax -> afterMax
            PickerIssueCodes.NotSelectable -> notSelectable
            PickerIssueCodes.DstGap -> dstGap
            PickerIssueCodes.OffsetRequired -> offsetRequired
            PickerIssueCodes.ExternalConflict -> externalConflict
            PickerIssueCodes.ZoneConflict -> zoneConflict
            PickerIssueCodes.PrecisionConflict -> precisionConflict
            PickerIssueCodes.CommitNotAccepted -> commitNotAccepted
            PickerIssueCodes.ValueOutOfRange -> valueOutOfRange
            PickerIssueCodes.TextTooLong -> textTooLong
            PickerIssueCodes.EndBeforeStart -> endBeforeStart
            PickerIssueCodes.NotReady -> notReady
            else -> issue.code
        }
        return issue.arguments.entries.fold(base) { message, (key, value) ->
            message.replace("{$key}", value)
        }
    }

    companion object {
        /**
         * 中文区域返回中文默认值，其他区域返回英文默认值。
         */
        fun forLocale(locale: Locale): PickerStrings =
            if (locale.language.equals(Locale.SIMPLIFIED_CHINESE.language, ignoreCase = true)) {
                PickerStrings()
            } else {
                PickerStrings(
                    required = "This field is required",
                    incomplete = "Enter a complete value",
                    invalidFormat = "The format is invalid",
                    invalidDate = "The date does not exist",
                    invalidTime = "The time is invalid",
                    beforeMin = "The value is before the allowed minimum",
                    afterMax = "The value is after the allowed maximum",
                    notSelectable = "This value cannot be selected",
                    dstGap = "This local time does not exist in the selected time zone",
                    offsetRequired = "Choose which offset applies to this local time",
                    externalConflict = "The value changed elsewhere; choose how to continue",
                    zoneConflict = "The time zone changed; confirm how to continue editing",
                    precisionConflict = "The display precision changed; resolve the input first",
                    commitNotAccepted = "The external value did not accept this change",
                    loadExternal = "Load latest value",
                    keepEditing = "Keep my edits",
                    valueOutOfRange = "The value is outside the supported range",
                    textTooLong = "The input is too long",
                    endBeforeStart = "The end must be after the start",
                    notReady = "Complete the input first",
                    hiddenPrecision = "The original value contains hidden precision; it is kept when time is unchanged",
                )
            }
    }
}
