package common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * 日期时间格式化工具
 *
 * 提供人类友好的日期格式化方法
 */
object DateTimeFormatters {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * 人类友好的相对日期格式化
     *
     * @param targetDate 目标日期
     * @param referenceDate 参考日期（通常为今天）
     * @return "今天"、"昨天"、"前天"、"X天前"、"X周前"、"X个月前"、或完整日期
     */
    fun formatRelativeDate(
        targetDate: LocalDate,
        referenceDate: LocalDate
    ): String {
        val daysDiff = ChronoUnit.DAYS.between(targetDate, referenceDate)
        val monthsDiff = ChronoUnit.MONTHS.between(targetDate, referenceDate)

        return when {
            daysDiff < 0L -> targetDate.format(dateFormatter) // 未来日期显示完整格式
            daysDiff == 0L -> "今天"
            daysDiff == 1L -> "昨天"
            daysDiff == 2L -> "前天"
            daysDiff in 3L..6L -> "${daysDiff}天前"
            daysDiff in 7L..13L -> "1周前"
            daysDiff in 14L..20L -> "2周前"
            daysDiff in 21L..29L -> "3周前"
            monthsDiff == 1L -> "1个月前"
            monthsDiff == 2L -> "2个月前"
            monthsDiff == 3L -> "3个月前"
            monthsDiff in 4L..5L -> "${monthsDiff}个月前"
            monthsDiff in 6L..8L -> "半年前"
            monthsDiff in 9L..11L -> "约1年前"
            else -> targetDate.format(dateFormatter)
        }
    }

    /**
     * 格式化时间（24小时制，无秒）
     *
     * @param time 时间
     * @return "HH:mm" 格式的时间字符串
     */
    fun formatTime(time: LocalTime): String {
        return time.format(timeFormatter)
    }

    /**
     * 格式化日期为中文格式
     *
     * @param date 日期
     * @return "yyyy年M月d日" 格式的日期字符串
     */
    fun formatDate(date: LocalDate): String {
        return date.format(dateFormatter)
    }

    /**
     * 格式化日期时间为友好格式
     *
     * @param dateTime 日期时间
     * @return "yyyy年M月d日 HH:mm" 格式的日期时间字符串
     */
    fun formatDateTime(dateTime: LocalDateTime): String {
        return "${formatDate(dateTime.toLocalDate())} ${formatTime(dateTime.toLocalTime())}"
    }
}
