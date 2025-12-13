package view.common.datetime

import androidx.compose.runtime.Stable
import java.time.Clock
import java.time.Instant
import java.util.*

// TODO

// @Stable // TODO
interface DateTimePickerState {
    /**
     * 选择器使用的 [Clock], 用来确认时区、回拨到当前信息等。
     */
    val clock: Clock

    /**
     * 部分信息（如星期）展示用的 [Locale]。
     */
    val locale: Locale?

    /**
     * 选择器当前选中的日期时间。
     */
    val selectedDateTime: Instant?

    /**
     * 选择器支持的年份范围。
     */
    val yearRange: IntRange

    /**
     * 选择器支持的日期验证器。
     */
    val selectableDateValidator: SelectableDateValidator?
}

/**
 * 日期验证器，用于控制日期选择器中可选的日期和年份。
 */
@Stable
interface SelectableDateValidator {
    fun isSelectableDate(year: Int, dayOfYear: Int): Boolean
    fun isSelectableYear(year: Int): Boolean
}
