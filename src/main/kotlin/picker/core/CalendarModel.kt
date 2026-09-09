package picker.core

import java.time.LocalDate
import java.time.YearMonth

/**
 * 单个日历单元格，包含选择与校验状态。
 */
data class CalendarDay(
    /**
     * 对应日期；超出范围的占位单元格为 `null`。
     */
    val date: LocalDate?,
    /**
     * 此单元格是否为当前本地日期。
     */
    val isToday: Boolean,
    /**
     * 此单元格是否为已提交或草稿中选中的日期。
     */
    val isSelected: Boolean,
    /**
     * 键盘焦点当前是否指向此日期。
     */
    val isFocused: Boolean,
    /**
     * 此日期是否属于相邻月份。
     */
    val isOutsideMonth: Boolean,
    /**
     * 此日期是否满足给定约束。
     */
    val isEnabled: Boolean,
    /**
     * 导致此日期不可用的首个校验问题；没有则为 `null`。
     */
    val issue: PickerIssue? = null,
)

/**
 * 用于展示一个月份的固定六行日历网格。
 */
data class CalendarGrid(
    /**
     * 网格中当月单元格所属的月份。
     */
    val displayedMonth: YearMonth,
    /**
     * 按展示顺序排列的 [CALENDAR_CELL_COUNT] 个单元格。
     */
    val days: List<CalendarDay>,
) {
    init {
        require(days.size == CALENDAR_CELL_COUNT) {
            "A picker month must contain exactly $CALENDAR_CELL_COUNT cells"
        }
    }
}

/**
 * 使用环境定义的每周起始日和一次采样的 [PickerNow] 构建固定大小的月网格。
 */
fun buildCalendarGrid(
    /**
     * 需要在网格主体中展示的月份。
     */
    displayedMonth: YearMonth,
    /**
     * 日历环境，包括每周起始日与时区。
     */
    environment: PickerEnvironment,
    /**
     * 应显示选中样式的日期。
     */
    selectedDate: LocalDate? = null,
    /**
     * 应显示键盘焦点样式的日期。
     */
    focusedDate: LocalDate? = null,
    /**
     * 决定各日期单元格是否可用的规则。
     */
    constraints: DateConstraints = DateConstraints(),
    /**
     * 用于“今天”样式和动态校验的当前时间快照。
     */
    now: PickerNow = environment.sampleNow(),
): CalendarGrid {
    val first = displayedMonth.atDay(1)
    val leading = Math.floorMod(
        first.dayOfWeek.value - environment.firstDayOfWeek.value,
        DAYS_PER_WEEK,
    )
    val gridStart = first.minusDays(leading.toLong())

    // 固定六行可避免月份横跨五周或六周时弹窗高度变化。
    val days = List(CALENDAR_CELL_COUNT) { index ->
        val candidate = runCatching { gridStart.plusDays(index.toLong()) }.getOrNull()
        if (candidate == null || candidate.year !in PICKER_MIN_YEAR..PICKER_MAX_YEAR) {
            CalendarDay(
                date = null,
                isToday = false,
                isSelected = false,
                isFocused = false,
                isOutsideMonth = true,
                isEnabled = false,
            )
        } else {
            val result = validateDate(candidate, constraints, now)
            CalendarDay(
                date = candidate,
                isToday = candidate == now.date,
                isSelected = candidate == selectedDate,
                isFocused = candidate == focusedDate,
                isOutsideMonth = YearMonth.from(candidate) != displayedMonth,
                isEnabled = result is ValidationResult.Valid,
                issue = (result as? ValidationResult.Invalid)?.issue,
            )
        }
    }
    return CalendarGrid(displayedMonth, days)
}

/**
 * 移动月份，超出选择器年份范围时返回 `null`。
 */
fun moveMonth(month: YearMonth, amount: Long): YearMonth? =
    runCatching { month.plusMonths(amount) }
        .getOrNull()
        ?.takeIf { it.year in PICKER_MIN_YEAR..PICKER_MAX_YEAR }

/**
 * 保持月份不变，按整年移动。
 */
fun moveYearMonth(month: YearMonth, amount: Long): YearMonth? =
    moveMonth(month, amount * 12)

/**
 * 将展示月份限制在与 [constraints] 相交的月份范围内。
 */
fun clampMonth(month: YearMonth, constraints: DateConstraints): YearMonth {
    val minimum = YearMonth.from(constraints.min)
    val maximum = YearMonth.from(constraints.max)
    return when {
        month < minimum -> minimum
        month > maximum -> maximum
        else -> month
    }
}

/**
 * 返回 [month] 中的有效日期，并将越界日数限制到有效范围。
 */
fun dateAtMonthDay(month: YearMonth, dayOfMonth: Int): LocalDate =
    month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

/**
 * 安全构造选择器支持的日期，输入无效时返回 `null`。
 */
fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
    if (year !in PICKER_MIN_YEAR..PICKER_MAX_YEAR) {
        null
    } else {
        runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }
