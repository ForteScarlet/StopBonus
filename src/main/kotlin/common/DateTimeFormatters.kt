package common

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

/**
 * 日期时间格式化工具
 *
 * 提供人类友好的日期格式化方法
 */
object DateTimeFormatters {

    private val dateFormatter = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.CHINA)
    // .ofPattern("yyyy年M月d日", Locale.CHINA)

    private val timeFormatter = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.CHINA)

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
