package picker.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import picker.core.DateConstraints
import picker.core.PICKER_MAX_YEAR
import picker.core.PICKER_MIN_YEAR
import picker.core.clampMonth
import picker.core.dateAtMonthDay
import picker.core.moveMonth
import picker.core.moveYearMonth
import java.time.LocalDate
import java.time.YearMonth

/**
 * 选择器支持的三种日历子视图。
 */
enum class CalendarView {
    /**
     * 六行日期网格。
     */
    Days,

    /**
     * 当前年份的十二个月份选择网格。
     */
    Months,

    /**
     * 当前年份页的十二年选择网格。
     */
    Years,
}

/**
 * 日期、月份与年份视图共用的有状态导航模型。
 *
 * 此模型只持有导航状态，选择结果仍由调用方控制，以便接入草稿字段或内嵌面板。
 */
class CalendarNavigationState internal constructor(
    initialMonth: YearMonth?,
    initialFocusedDate: LocalDate?,
) {
    private var initialized = false

    /**
     * 日期网格或月份选择器当前显示的月份。
     */
    var displayedMonth: YearMonth by mutableStateOf(
        initialMonth ?: YearMonth.of(PICKER_MIN_YEAR, 1),
    )
        private set

    /**
     * 当前获得键盘焦点的日期；尚未建立焦点时为空。
     */
    var focusedDate by mutableStateOf(initialFocusedDate)
        private set

    /**
     * 当前显示的日历子视图。
     */
    var view by mutableStateOf(CalendarView.Days)
        private set

    /**
     * 清理一次弹层会话的导航状态，但保留模型实例。
     */
    internal fun prepareForOpening() {
        initialized = false
        focusedDate = null
        view = CalendarView.Days
    }

    fun initialize(
        /**
         * 用作初始焦点目标的当前字段值。
         */
        value: LocalDate?,
        /**
         * 字段无值时使用的当前本地日期。
         */
        today: LocalDate,
        /**
         * 用于选择初始显示月份的范围。
         */
        constraints: DateConstraints,
    ) {
        if (initialized) return
        initialized = true
        // 每次打开弹层仅初始化一次，避免重组把焦点拉回并覆盖用户导航。
        if (focusedDate == null) {
            focusedDate = value ?: today
        }
        displayedMonth = clampMonth(
            YearMonth.from(focusedDate ?: today),
            constraints,
        )
    }

    /**
     * 切换到日期网格。
     */
    fun showDays() {
        view = CalendarView.Days
    }

    /**
     * 切换到月份选择网格。
     */
    fun showMonths() {
        view = CalendarView.Months
    }

    /**
     * 切换到年份选择网格。
     */
    fun showYears() {
        view = CalendarView.Years
    }

    /**
     * 移动显示月份，并返回是否实际发生移动。
     */
    fun moveDisplayedMonth(amount: Long, constraints: DateConstraints): Boolean {
        val next = moveMonth(displayedMonth, amount) ?: return false
        val bounded = clampMonth(next, constraints)
        if (bounded == displayedMonth) return false
        displayedMonth = bounded
        focusedDate = focusedDate?.let { dateAtMonthDay(bounded, it.dayOfMonth) }
        return true
    }

    /**
     * 移动显示年份，并返回是否实际发生移动。
     */
    fun moveDisplayedYear(amount: Long, constraints: DateConstraints): Boolean {
        val next = moveYearMonth(displayedMonth, amount) ?: return false
        val bounded = clampMonth(next, constraints)
        if (bounded == displayedMonth) return false
        displayedMonth = bounded
        focusedDate = focusedDate?.let { dateAtMonthDay(bounded, it.dayOfMonth) }
        return true
    }

    /**
     * 在约束范围内按天数移动键盘焦点。
     */
    fun moveFocusedDays(amount: Long, constraints: DateConstraints): Boolean {
        val current = focusedDate ?: displayedMonth.atDay(1)
        val next = runCatching { current.plusDays(amount) }.getOrNull() ?: return false
        if (next.year !in PICKER_MIN_YEAR..PICKER_MAX_YEAR) return false
        if (next < constraints.min || next > constraints.max) return false
        focusedDate = next
        displayedMonth = YearMonth.from(next)
        return true
    }

    /**
     * 按日历月数移动键盘焦点。
     */
    fun moveFocusedMonths(amount: Long, constraints: DateConstraints): Boolean {
        val current = focusedDate ?: displayedMonth.atDay(1)
        val next = moveMonth(YearMonth.from(current), amount) ?: return false
        val bounded = clampMonth(next, constraints)
        if (bounded == YearMonth.from(current)) return false
        displayedMonth = bounded
        focusedDate = dateAtMonthDay(bounded, current.dayOfMonth)
        return true
    }

    /**
     * 按日历年数移动键盘焦点。
     */
    fun moveFocusedYears(amount: Long, constraints: DateConstraints): Boolean {
        val current = focusedDate ?: displayedMonth.atDay(1)
        val next = moveYearMonth(YearMonth.from(current), amount) ?: return false
        val bounded = clampMonth(next, constraints)
        if (bounded == YearMonth.from(current)) return false
        displayedMonth = bounded
        focusedDate = dateAtMonthDay(bounded, current.dayOfMonth)
        return true
    }

    /**
     * 聚焦受支持的日期并显示其所在月份。
     */
    fun focus(date: LocalDate) {
        if (date.year in PICKER_MIN_YEAR..PICKER_MAX_YEAR) {
            focusedDate = date
            displayedMonth = YearMonth.from(date)
        }
    }

    /**
     * 仅在月份至少有一天落入约束范围时选择该月。
     */
    fun selectMonth(month: Int, constraints: DateConstraints): Boolean {
        val next = runCatching { YearMonth.of(displayedMonth.year, month) }.getOrNull() ?: return false
        if (next.atEndOfMonth() < constraints.min || next.atDay(1) > constraints.max) return false
        displayedMonth = clampMonth(next, constraints)
        view = CalendarView.Days
        focusedDate = focusedDate?.let { dateAtMonthDay(displayedMonth, it.dayOfMonth) }
        return true
    }

    /**
     * 仅在年份至少有一天落入约束范围时选择该年。
     */
    fun selectYear(year: Int, constraints: DateConstraints): Boolean {
        if (year !in PICKER_MIN_YEAR..PICKER_MAX_YEAR) return false
        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)
        if (yearEnd < constraints.min || yearStart > constraints.max) return false
        displayedMonth = clampMonth(YearMonth.of(year, displayedMonth.monthValue), constraints)
        view = CalendarView.Days
        focusedDate = focusedDate?.let { dateAtMonthDay(displayedMonth, it.dayOfMonth) }
        return true
    }
}

/**
 * 记住使用给定初始焦点参数的导航模型。
 */
@Composable
fun rememberCalendarNavigationState(
    /**
     * 首次初始化前显示的可选初始月份。
     */
    initialMonth: YearMonth? = null,
    /**
     * 可选的初始键盘焦点日期。
     */
    initialFocusedDate: LocalDate? = null,
): CalendarNavigationState = remember(initialMonth, initialFocusedDate) {
    CalendarNavigationState(initialMonth, initialFocusedDate)
}
