package picker.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * [LocalTime] 在选择器格式下可见的部分。
 */
data class VisibleTime(
    /**
     * 二十四小时制下可见的小时。
     */
    val hour: Int,
    /**
     * 可见的分钟。
     */
    val minute: Int,
    /**
     * 可见的秒；分钟精度下为 `null`。
     */
    val second: Int?,
)

/**
 * 将时间转换为用户实际可编辑的字段。
 */
fun LocalTime.visibleTime(options: TimeFormatOptions): VisibleTime =
    VisibleTime(
        hour = hour,
        minute = minute,
        second = if (options.precision == TimePrecision.Second) second else null,
    )

/**
 * 判断时间在当前显示精度下是否发生变化。
 */
fun visibleTimeChanged(
    before: LocalTime,
    after: LocalTime,
    options: TimeFormatOptions,
): Boolean = before.visibleTime(options) != after.visibleTime(options)

/**
 * 移除当前编辑精度无法表示的秒或纳秒。
 */
fun truncateForPrecision(
    value: LocalTime,
    options: TimeFormatOptions,
): LocalTime = when (options.precision) {
    TimePrecision.Minute -> LocalTime.of(value.hour, value.minute)
    TimePrecision.Second -> LocalTime.of(value.hour, value.minute, value.second)
}

/**
 * 将编辑后的可见时间合并到原值。
 *
 * 可见字段未变化时直接返回原值，避免仅打开选择器便丢失隐藏的秒或纳秒。
 */
fun mergeEditedTime(
    original: LocalTime?,
    edited: LocalTime,
    options: TimeFormatOptions,
    timeWasActuallyChanged: Boolean,
): LocalTime {
    if (!timeWasActuallyChanged && original != null) return original
    return truncateForPrecision(edited, options)
}

/**
 * 替换日期，并尽可能保留原始时间精度。
 */
fun mergeDatePreservingTime(
    date: LocalDate,
    original: LocalDateTime?,
    options: TimeFormatOptions,
    timeWasActuallyChanged: Boolean = false,
): LocalDateTime? {
    if (original == null) return null
    return LocalDateTime.of(
        date,
        mergeEditedTime(
            original = original.toLocalTime(),
            edited = original.toLocalTime(),
            options = options,
            timeWasActuallyChanged = timeWasActuallyChanged,
        ),
    )
}

/**
 * 将十二小时制小时和时段转换为二十四小时制小时。
 */
fun hour12To24(hour: Int, periodIsPm: Boolean): Int =
    (hour % 12) + if (periodIsPm) 12 else 0

/**
 * 将二十四小时制小时转换为对应的十二小时制小时。
 */
fun hour24To12(hour: Int): Int = ((hour + 11) % 12) + 1

/**
 * 判断二十四小时制小时是否属于下午时段。
 */
fun periodIsPm(hour: Int): Boolean = hour >= 12

/**
 * 判断值是否包含被 [options] 隐藏的精度。
 */
fun hiddenPrecisionDescription(
    original: LocalTime?,
    options: TimeFormatOptions,
): Boolean = original != null && when (options.precision) {
    TimePrecision.Minute -> original.second != 0 || original.nano != 0
    TimePrecision.Second -> original.nano != 0
}
