package picker.core

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * 选择器日历可表示的最小年份。
 */
const val PICKER_MIN_YEAR: Int = 1

/**
 * 选择器日历可表示的最大年份。
 */
const val PICKER_MAX_YEAR: Int = 9999

/**
 * 选择器编解码器接受的最大文本长度。
 */
const val MAX_PICKER_TEXT_LENGTH: Int = 256

/**
 * 一周的天数。
 */
const val DAYS_PER_WEEK: Int = 7

/**
 * 月历固定展示的行数。
 */
const val CALENDAR_ROW_COUNT: Int = 6

/**
 * 月历固定展示的单元格数，包括相邻月份日期。
 */
const val CALENDAR_CELL_COUNT: Int = DAYS_PER_WEEK * CALENDAR_ROW_COUNT

/**
 * 选择器解析、校验、日历导航以及“今天”“现在”等相对操作共享的运行环境。
 *
 * 显式提供这些值可确保测试行为确定，并避免组件混用系统时区与业务时区。
 */
data class PickerEnvironment(
    /**
     * 组件采样当前时刻时使用的时钟。
     */
    val clock: Clock,
    /**
     * 将时刻转换为选择器所示本地日期时间的时区。
     */
    val zoneId: ZoneId = clock.zone,
    /**
     * 用于本地化文本和编解码器输入规范化的区域设置。
     */
    val locale: Locale = Locale.SIMPLIFIED_CHINESE,
    /**
     * 日历网格首列对应的星期。
     */
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
)

/**
 * 一次一致的当前时间采样。
 *
 * 同时记录绝对时刻及其本地映射，避免一次交互的校验跨越日期或夏令时边界。
 */
data class PickerNow(
    /**
     * 采样得到的绝对时刻。
     */
    val instant: Instant,
    /**
     * 采样时刻在 [PickerEnvironment.zoneId] 中的本地映射。
     */
    val localDateTime: LocalDateTime,
) {
    /**
     * [instant] 对应的本地日期。
     */
    val date: LocalDate
        get() = localDateTime.toLocalDate()

    /**
     * [instant] 对应的本地墙上时间。
     */
    val time: LocalTime
        get() = localDateTime.toLocalTime()
}

/**
 * 仅采样时钟一次，并从同一时刻派生所有本地字段。
 */
fun PickerEnvironment.sampleNow(): PickerNow {
    val instant = clock.instant()
    return PickerNow(
        instant = instant,
        localDateTime = instant.atZone(zoneId).toLocalDateTime(),
    )
}

/**
 * 控制时间选择器仅显示分钟还是同时显示秒。
 */
enum class TimePrecision {
    /**
     * 显示并编辑小时和分钟。
     */
    Minute,

    /**
     * 显示并编辑小时、分钟和秒。
     */
    Second,
}

/**
 * 控制时间编辑器使用二十四小时制还是十二小时制。
 */
enum class HourCycle {
    /**
     * 使用 00:00 至 23:59。
     */
    H24,

    /**
     * 使用上午 1:00 至下午 12:59。
     */
    H12,
}

/**
 * 时间与日期时间编解码器共享的格式化和编辑规则。
 */
data class TimeFormatOptions(
    /**
     * 选择器中可见且可编辑的精度。
     */
    val precision: TimePrecision = TimePrecision.Minute,
    /**
     * 解析、格式化及分段编辑器使用的小时制。
     */
    val hourCycle: HourCycle = HourCycle.H24,
)
