package picker.foundation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import picker.core.CalendarDay
import picker.core.DateConstraints
import picker.core.PICKER_MAX_YEAR
import picker.core.PICKER_MIN_YEAR
import picker.core.PickerEnvironment
import picker.core.ValidationResult
import picker.core.buildCalendarGrid
import picker.core.clampMonth
import picker.core.moveMonth
import picker.core.moveYearMonth
import picker.core.validateDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * 传入自定义日期格槽位的公开展示模型。
 */
data class CalendarDayPresentation(
    /**
     * 此格表示的日期。
     */
    val date: LocalDate,
    /**
     * 此日期是否为当前本地日期。
     */
    val isToday: Boolean,
    /**
     * 此日期是否已选中。
     */
    val isSelected: Boolean,
    /**
     * 键盘焦点是否指向此日期。
     */
    val isFocused: Boolean,
    /**
     * 此日期是否属于相邻月份。
     */
    val isOutsideMonth: Boolean,
    /**
     * 此日期是否满足日期约束。
     */
    val isEnabled: Boolean,
    /**
     * 说明日期被禁用原因的校验问题；适用时提供。
     */
    val issue: picker.core.PickerIssue?,
    /**
     * 选择此日期；日期格禁用时为安全空操作。
     */
    val onClick: () -> Unit,
    /**
     * 默认皮肤为此日期格状态选定的文字颜色。
     */
    val contentColor: Color = Color(0xFF0F172A),
)

/**
 * 传入自定义日历标题槽位的作用域。
 */
class CalendarHeaderScope internal constructor(
    /**
     * 日历当前显示的月份。
     */
    val displayedMonth: YearMonth,
    /**
     * 上一个导航动作能否移动日历。
     */
    val canGoPrevious: Boolean,
    /**
     * 下一个导航动作能否移动日历。
     */
    val canGoNext: Boolean,
    /**
     * 移至上一个月、年或年份页。
     */
    val previous: () -> Unit,
    /**
     * 移至下一个月、年或年份页。
     */
    val next: () -> Unit,
    /**
     * 切换到月份选择视图。
     */
    val showMonths: () -> Unit,
    /**
     * 切换到年份选择视图。
     */
    val showYears: () -> Unit,
)

/**
 * 具有固定 42 格日期网格、月/年子视图及键盘导航的 Foundation 日历。
 *
 * 它刻意只使用 Compose Foundation 原语，使调用方可通过参数和槽位控制视觉皮肤。
 */
@Composable
fun BasicCalendar(
    /**
     * 当前选中日期。
     */
    value: LocalDate?,
    /**
     * 接收日期选择；调用方清空时为 null。
     */
    onValueChange: (LocalDate?) -> Unit,
    /**
     * 此日历的时钟、时区、语言环境及每周起始日配置。
     */
    environment: PickerEnvironment,
    /**
     * 应用于日历根节点的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 跨重组保留的导航模型。
     */
    navigationState: CalendarNavigationState = rememberCalendarNavigationState(),
    /**
     * 日期闭区间与附加选择规则。
     */
    constraints: DateConstraints = DateConstraints(),
    /**
     * 日历是否接受输入。
     */
    enabled: Boolean = true,
    /**
     * 日历是否可见但不可编辑。
     */
    readOnly: Boolean = false,
    /**
     * 选中日期背景色。
     */
    selectedColor: Color = Color(0xFF0F766E),
    /**
     * 选中日期文字颜色。
     */
    selectedContentColor: Color = Color.White,
    /**
     * 未选中“今天”的背景色。
     */
    todayColor: Color = Color(0xFFE6F4F1),
    /**
     * 键盘焦点日期的描边颜色。
     */
    focusColor: Color = Color(0xFF0F766E),
    /**
     * 当月普通日期文字颜色。
     */
    onSurfaceColor: Color = Color(0xFF0F172A),
    /**
     * 禁用日期文字颜色。
     */
    disabledContentColor: Color = Color(0xFF64748B),
    /**
     * 显示月份之外日期的文字颜色。
     */
    outsideMonthColor: Color = onSurfaceColor.copy(alpha = 0.55f),
    /**
     * 每个日历格的最小尺寸。
     */
    cellMinSize: Dp = 36.dp,
    /**
     * 在标准交互外壳内渲染的自定义日期格内容。
     */
    dayContent: @Composable (CalendarDayPresentation) -> Unit = { presentation ->
        DefaultCalendarDay(presentation)
    },
    /**
     * 渲染在日历视图上方的自定义标题内容。
     */
    header: @Composable (CalendarHeaderScope) -> Unit = { scope ->
        DefaultCalendarHeader(scope)
    },
) {
    val now = rememberPickerNow(environment)
    navigationState.initialize(value, now.date, constraints)
    val minimumMonth = YearMonth.from(constraints.min)
    val maximumMonth = YearMonth.from(constraints.max)
    val focusedDate = navigationState.focusedDate ?: value ?: now.date
    val dayFocusRequester = remember { FocusRequester() }
    val subviewFocusRequester = remember { FocusRequester() }

    // 焦点目标随子视图改变；在此请求焦点可让日/月/年切换后继续键盘导航。
    LaunchedEffect(navigationState.view) {
        if (navigationState.view == CalendarView.Days) {
            dayFocusRequester.requestFocus()
        } else {
            subviewFocusRequester.requestFocus()
        }
    }

    Column(modifier = modifier) {
        header(
            CalendarHeaderScope(
                displayedMonth = navigationState.displayedMonth,
                canGoPrevious = canNavigate(
                    view = navigationState.view,
                    displayedMonth = navigationState.displayedMonth,
                    amount = -1,
                    minimumMonth = minimumMonth,
                    maximumMonth = maximumMonth,
                    constraints = constraints,
                ),
                canGoNext = canNavigate(
                    view = navigationState.view,
                    displayedMonth = navigationState.displayedMonth,
                    amount = 1,
                    minimumMonth = minimumMonth,
                    maximumMonth = maximumMonth,
                    constraints = constraints,
                ),
                previous = {
                    if (enabled && !readOnly) {
                        when (navigationState.view) {
                            CalendarView.Years -> navigationState.moveDisplayedYear(-12, constraints)
                            CalendarView.Months -> navigationState.moveDisplayedYear(-1, constraints)
                            CalendarView.Days -> navigationState.moveDisplayedMonth(-1, constraints)
                        }
                    }
                },
                next = {
                    if (enabled && !readOnly) {
                        when (navigationState.view) {
                            CalendarView.Years -> navigationState.moveDisplayedYear(12, constraints)
                            CalendarView.Months -> navigationState.moveDisplayedYear(1, constraints)
                            CalendarView.Days -> navigationState.moveDisplayedMonth(1, constraints)
                        }
                    }
                },
                showMonths = {
                    if (enabled && !readOnly) navigationState.showMonths()
                },
                showYears = {
                    if (enabled && !readOnly) navigationState.showYears()
                },
            )
        )
        AnimatedContent(
            targetState = navigationState.view,
            label = "日历视图切换",
        ) { view ->
            when (view) {
                CalendarView.Days -> {
                    val grid = buildCalendarGrid(
                        displayedMonth = navigationState.displayedMonth,
                        environment = environment,
                        selectedDate = value,
                        focusedDate = focusedDate,
                        constraints = constraints,
                        now = now,
                    )
                    Column {
                        WeekHeader(
                            environment = environment,
                            cellMinSize = cellMinSize,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        CalendarGrid(
                            grid = grid,
                            environment = environment,
                            enabled = enabled,
                            readOnly = readOnly,
                            onValueChange = onValueChange,
                            navigationState = navigationState,
                            dayContent = dayContent,
                            selectedColor = selectedColor,
                            selectedContentColor = selectedContentColor,
                            todayColor = todayColor,
                            focusColor = focusColor,
                            onSurfaceColor = onSurfaceColor,
                            disabledContentColor = disabledContentColor,
                            outsideMonthColor = outsideMonthColor,
                            cellMinSize = cellMinSize,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(dayFocusRequester)
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (!enabled || readOnly || event.type != KeyEventType.KeyDown) {
                                        return@onPreviewKeyEvent false
                                    }
                                    // 方向键遵循物理日历网格，翻页键移动当前显示范围。
                                    when (event.key) {
                                        Key.DirectionLeft -> navigationState.moveFocusedDays(-1, constraints)
                                        Key.DirectionRight -> navigationState.moveFocusedDays(1, constraints)
                                        Key.DirectionUp -> navigationState.moveFocusedDays(-7, constraints)
                                        Key.DirectionDown -> navigationState.moveFocusedDays(7, constraints)
                                        Key.MoveHome -> moveToWeekBoundary(
                                            navigationState,
                                            environment,
                                            constraints,
                                            false
                                        )

                                        Key.MoveEnd -> moveToWeekBoundary(
                                            navigationState,
                                            environment,
                                            constraints,
                                            true
                                        )

                                        Key.PageUp -> if (event.isShiftPressed) {
                                            navigationState.moveFocusedYears(-1, constraints)
                                        } else {
                                            navigationState.moveDisplayedMonth(-1, constraints)
                                        }

                                        Key.PageDown -> if (event.isShiftPressed) {
                                            navigationState.moveFocusedYears(1, constraints)
                                        } else {
                                            navigationState.moveDisplayedMonth(1, constraints)
                                        }

                                        Key.Enter, Key.Spacebar -> {
                                            val focused = navigationState.focusedDate
                                            if (
                                                focused != null &&
                                                validateDate(focused, constraints, now) is ValidationResult.Valid
                                            ) {
                                                onValueChange(focused)
                                                true
                                            } else {
                                                false
                                            }
                                        }

                                        else -> false
                                    }
                                },
                        )
                    }
                }

                CalendarView.Months -> MonthGrid(
                    displayedMonth = navigationState.displayedMonth,
                    focusedDate = navigationState.focusedDate,
                    constraints = constraints,
                    enabled = enabled,
                    readOnly = readOnly,
                    locale = environment.locale,
                    selectedColor = selectedColor,
                    selectedContentColor = selectedContentColor,
                    onSurfaceColor = onSurfaceColor,
                    cellMinSize = cellMinSize,
                    navigationState = navigationState,
                    focusRequester = subviewFocusRequester,
                    onSelect = { month -> navigationState.selectMonth(month, constraints) },
                )

                CalendarView.Years -> YearGrid(
                    displayedMonth = navigationState.displayedMonth,
                    focusedDate = navigationState.focusedDate,
                    constraints = constraints,
                    enabled = enabled,
                    readOnly = readOnly,
                    selectedColor = selectedColor,
                    selectedContentColor = selectedContentColor,
                    onSurfaceColor = onSurfaceColor,
                    cellMinSize = cellMinSize,
                    navigationState = navigationState,
                    focusRequester = subviewFocusRequester,
                    onSelect = { year -> navigationState.selectYear(year, constraints) },
                )
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    grid: picker.core.CalendarGrid,
    environment: PickerEnvironment,
    enabled: Boolean,
    readOnly: Boolean,
    onValueChange: (LocalDate?) -> Unit,
    navigationState: CalendarNavigationState,
    dayContent: @Composable (CalendarDayPresentation) -> Unit,
    selectedColor: Color,
    selectedContentColor: Color,
    todayColor: Color,
    focusColor: Color,
    onSurfaceColor: Color,
    disabledContentColor: Color,
    outsideMonthColor: Color,
    cellMinSize: Dp,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        grid.days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        environment = environment,
                        enabled = enabled,
                        readOnly = readOnly,
                        onClick = {
                            navigationState.focus(day.date!!)
                            onValueChange(day.date)
                        },
                        dayContent = dayContent,
                        selectedColor = selectedColor,
                        selectedContentColor = selectedContentColor,
                        todayColor = todayColor,
                        focusColor = focusColor,
                        onSurfaceColor = onSurfaceColor,
                        disabledContentColor = disabledContentColor,
                        outsideMonthColor = outsideMonthColor,
                        modifier = Modifier.weight(1f),
                        cellMinSize = cellMinSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDay,
    environment: PickerEnvironment,
    enabled: Boolean,
    readOnly: Boolean,
    onClick: () -> Unit,
    dayContent: @Composable (CalendarDayPresentation) -> Unit,
    selectedColor: Color,
    selectedContentColor: Color,
    todayColor: Color,
    focusColor: Color,
    onSurfaceColor: Color,
    disabledContentColor: Color,
    outsideMonthColor: Color,
    cellMinSize: Dp,
    modifier: Modifier,
) {
    val date = day.date
    if (date == null) {
        Box(modifier = modifier.size(cellMinSize))
        return
    }
    val label = buildString {
        append(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        append(' ')
        append(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, environment.locale))
        when {
            day.isSelected -> append("，已选中")
            day.isToday -> append("，今天")
        }
        if (!day.isEnabled) append("，不可选")
        day.issue?.let { issue ->
            append("，")
            append(picker.core.PickerStrings.forLocale(environment.locale).message(issue))
        }
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = cellMinSize, minHeight = cellMinSize)
            .padding(1.dp)
            .background(
                color = when {
                    day.isSelected -> selectedColor
                    day.isToday -> todayColor
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(6.dp),
            )
            .then(
                if (day.isFocused) {
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = focusColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                        )
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (day.isToday) {
                    Modifier.drawBehind {
                        drawCircle(
                            color = if (day.isSelected) selectedContentColor else focusColor,
                            radius = 2.dp.toPx(),
                            center = Offset(size.width / 2f, size.height - 4.dp.toPx()),
                        )
                    }
                } else {
                    Modifier
                }
            )
            .semantics(mergeDescendants = true) {
                contentDescription = label
                testTag = PickerSemantics.day(date)
                selected = day.isSelected
                role = Role.Button
                if (!day.isEnabled || !enabled || readOnly) disabled()
            }
            .clickable(
                enabled = enabled && !readOnly && day.isEnabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 槽位同时收到受保护回调与禁用视觉状态，不能绕过日历契约。
        val safeOnClick = if (enabled && !readOnly && day.isEnabled) onClick else ({})
        dayContent(
            CalendarDayPresentation(
                date = date,
                isToday = day.isToday,
                isSelected = day.isSelected,
                isFocused = day.isFocused,
                isOutsideMonth = day.isOutsideMonth,
                isEnabled = day.isEnabled,
                issue = day.issue,
                onClick = safeOnClick,
                contentColor = when {
                    day.isSelected -> selectedContentColor
                    !day.isEnabled -> disabledContentColor
                    day.isOutsideMonth -> outsideMonthColor
                    else -> onSurfaceColor
                },
            )
        )
    }
}

@Composable
private fun DefaultCalendarDay(presentation: CalendarDayPresentation) {
    BasicText(
        text = presentation.date.dayOfMonth.toString(),
        style = TextStyle(
            color = presentation.contentColor,
        ),
    )
}

@Composable
private fun DefaultCalendarHeader(scope: CalendarHeaderScope) {
    val yearText = scope.displayedMonth.year.toString() + "年"
    val monthText = scope.displayedMonth.monthValue.toString() + "月"
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicCalendarChevron(
            direction = ChevronDirection.Left,
            enabled = scope.canGoPrevious,
            contentDescription = "上一个月",
            onClick = scope.previous,
        )
        BasicText(
            text = yearText,
            modifier = Modifier
                .clickable(onClick = scope.showYears)
                .semantics { contentDescription = "选择年份" },
        )
        BasicText(
            text = monthText,
            modifier = Modifier
                .clickable(onClick = scope.showMonths)
                .semantics { contentDescription = "选择月份" },
        )
        BasicCalendarChevron(
            direction = ChevronDirection.Right,
            enabled = scope.canGoNext,
            contentDescription = "下一个月",
            onClick = scope.next,
        )
    }
}

private enum class ChevronDirection {
    Left,
    Right,
}

@Composable
private fun BasicCalendarChevron(
    direction: ChevronDirection,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Canvas(
        modifier = Modifier
            .size(32.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                if (!enabled) disabled()
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val halfWidth = size.width * 0.18f
        val halfHeight = size.height * 0.18f
        val color = if (enabled) Color(0xFF0F172A) else Color(0xFF64748B)
        val cap = StrokeCap.Round
        val points = if (direction == ChevronDirection.Left) {
            listOf(
                Offset(center.x + halfWidth, center.y - halfHeight),
                center,
                Offset(center.x + halfWidth, center.y + halfHeight),
            )
        } else {
            listOf(
                Offset(center.x - halfWidth, center.y - halfHeight),
                center,
                Offset(center.x - halfWidth, center.y + halfHeight),
            )
        }
        drawLine(color, points[0], points[1], strokeWidth = 1.8.dp.toPx(), cap = cap)
        drawLine(color, points[1], points[2], strokeWidth = 1.8.dp.toPx(), cap = cap)
    }
}

@Composable
private fun WeekHeader(
    environment: PickerEnvironment,
    cellMinSize: Dp,
    modifier: Modifier,
) {
    val labels = remember(environment.locale, environment.firstDayOfWeek) {
        (0 until 7).map { offset ->
            val day = environment.firstDayOfWeek.plus(offset.toLong())
            day.getDisplayName(java.time.format.TextStyle.NARROW_STANDALONE, environment.locale) to
                    day.getDisplayName(java.time.format.TextStyle.FULL, environment.locale)
        }
    }
    // 星期栏与日期格共享同一最小行高，避免受父级收缩或视图切换动画影响而覆盖第一周。
    Row(
        modifier = modifier.defaultMinSize(minHeight = cellMinSize),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        labels.forEach { (label, description) ->
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = cellMinSize)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(label)
            }
        }
    }
}

@Composable
private fun MonthGrid(
    displayedMonth: YearMonth,
    focusedDate: LocalDate?,
    constraints: DateConstraints,
    enabled: Boolean,
    readOnly: Boolean,
    locale: java.util.Locale,
    selectedColor: Color,
    selectedContentColor: Color,
    onSurfaceColor: Color,
    cellMinSize: Dp,
    navigationState: CalendarNavigationState,
    focusRequester: FocusRequester,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!enabled || readOnly || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionLeft -> navigationState.moveFocusedMonths(-1, constraints)
                    Key.DirectionRight -> navigationState.moveFocusedMonths(1, constraints)
                    Key.DirectionUp -> navigationState.moveFocusedMonths(-3, constraints)
                    Key.DirectionDown -> navigationState.moveFocusedMonths(3, constraints)
                    Key.Enter, Key.Spacebar -> {
                        val month = navigationState.focusedDate?.monthValue
                            ?: displayedMonth.monthValue
                        val selectable = monthIntersectsConstraints(
                            YearMonth.of(displayedMonth.year, month),
                            constraints,
                        )
                        if (selectable) onSelect(month)
                        selectable
                    }

                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        (1..12).chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { month ->
                    val yearMonth = YearMonth.of(displayedMonth.year, month)
                    val isEnabled = monthIntersectsConstraints(yearMonth, constraints)
                    CalendarChoiceCell(
                        text = java.time.Month.of(month)
                            .getDisplayName(java.time.format.TextStyle.SHORT, locale),
                        contentDescription = yearMonth.toString(),
                        selected = focusedDate?.year == displayedMonth.year &&
                                focusedDate.monthValue == month,
                        enabled = isEnabled && enabled && !readOnly,
                        onClick = { onSelect(month) },
                        selectedColor = selectedColor,
                        selectedContentColor = selectedContentColor,
                        onSurfaceColor = onSurfaceColor,
                        cellMinSize = cellMinSize,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun YearGrid(
    displayedMonth: YearMonth,
    focusedDate: LocalDate?,
    constraints: DateConstraints,
    enabled: Boolean,
    readOnly: Boolean,
    selectedColor: Color,
    selectedContentColor: Color,
    onSurfaceColor: Color,
    cellMinSize: Dp,
    navigationState: CalendarNavigationState,
    focusRequester: FocusRequester,
    onSelect: (Int) -> Unit,
) {
    // 年份按十二个一页排列，以保持子视图稳定，并在 9999 年边界保留可预期的空格。
    val pageStart = ((displayedMonth.year - PICKER_MIN_YEAR) / 12) * 12 + PICKER_MIN_YEAR
    var yearInput by remember(pageStart) { mutableStateOf(TextFieldValue()) }
    var yearInputFocused by remember(pageStart) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (
                    !enabled ||
                    readOnly ||
                    yearInputFocused ||
                    event.type != KeyEventType.KeyDown
                ) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionLeft -> navigationState.moveFocusedYears(-1, constraints)
                    Key.DirectionRight -> navigationState.moveFocusedYears(1, constraints)
                    Key.DirectionUp -> navigationState.moveFocusedYears(-4, constraints)
                    Key.DirectionDown -> navigationState.moveFocusedYears(4, constraints)
                    Key.PageUp -> navigationState.moveDisplayedYear(-12, constraints)
                    Key.PageDown -> navigationState.moveDisplayedYear(12, constraints)
                    Key.Enter, Key.Spacebar -> {
                        val year = navigationState.focusedDate?.year ?: displayedMonth.year
                        val selectable = yearIntersectsConstraints(year, constraints)
                        if (selectable) onSelect(year)
                        selectable
                    }

                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = yearInput,
            onValueChange = { next ->
                if (next.text.length <= 4 && next.text.all(Char::isDigit)) {
                    yearInput = next
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF94A3B8), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .onFocusChanged { yearInputFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.Enter &&
                        enabled &&
                        !readOnly &&
                        yearInput.composition == null
                    ) {
                        val entered = yearInput.text.toIntOrNull()
                        if (
                            entered != null &&
                            entered in PICKER_MIN_YEAR..PICKER_MAX_YEAR
                        ) {
                            onSelect(entered)
                            yearInput = TextFieldValue()
                        }
                        true
                    } else {
                        false
                    }
                }
                .semantics { contentDescription = "输入年份" },
            enabled = enabled,
            readOnly = readOnly,
            singleLine = true,
            textStyle = TextStyle(color = Color(0xFF0F172A)),
        )
        (0 until 12).chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { offset ->
                    val year = pageStart + offset
                    if (year > PICKER_MAX_YEAR) {
                        Box(Modifier.weight(1f).defaultMinSize(minHeight = cellMinSize))
                    } else {
                        val isEnabled = yearIntersectsConstraints(year, constraints)
                        CalendarChoiceCell(
                            text = year.toString(),
                            contentDescription = year.toString(),
                            selected = focusedDate?.year == year,
                            enabled = isEnabled && enabled && !readOnly,
                            onClick = { onSelect(year) },
                            selectedColor = selectedColor,
                            selectedContentColor = selectedContentColor,
                            onSurfaceColor = onSurfaceColor,
                            cellMinSize = cellMinSize,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarChoiceCell(
    text: String,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    selectedColor: Color,
    selectedContentColor: Color,
    onSurfaceColor: Color,
    cellMinSize: Dp,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = cellMinSize, minHeight = cellMinSize)
            .background(
                color = if (selected) selectedColor else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                this.selected = selected
                role = Role.Button
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = if (selected) selectedContentColor else onSurfaceColor,
            ),
        )
    }
}

private fun monthIntersectsConstraints(
    month: YearMonth,
    constraints: DateConstraints,
): Boolean =
    month.atEndOfMonth() >= constraints.min && month.atDay(1) <= constraints.max

private fun yearIntersectsConstraints(
    year: Int,
    constraints: DateConstraints,
): Boolean {
    val first = LocalDate.of(year, 1, 1)
    val last = LocalDate.of(year, 12, 31)
    return last >= constraints.min && first <= constraints.max
}

private fun canNavigate(
    view: CalendarView,
    displayedMonth: YearMonth,
    amount: Long,
    minimumMonth: YearMonth,
    maximumMonth: YearMonth,
    constraints: DateConstraints,
): Boolean {
    val target = when (view) {
        CalendarView.Days -> moveMonth(displayedMonth, amount)
        CalendarView.Months -> moveYearMonth(displayedMonth, amount)
        CalendarView.Years -> moveYearMonth(displayedMonth, amount * 12)
    } ?: return false
    val bounded = clampMonth(target, constraints)
    return bounded in minimumMonth..maximumMonth && bounded != displayedMonth
}

private fun moveToWeekBoundary(
    state: CalendarNavigationState,
    environment: PickerEnvironment,
    constraints: DateConstraints,
    end: Boolean,
): Boolean {
    val focused = state.focusedDate ?: return false
    val distance = Math.floorMod(
        focused.dayOfWeek.value - environment.firstDayOfWeek.value,
        7,
    )
    return state.moveFocusedDays(
        if (end) (6 - distance).toLong() else -distance.toLong(),
        constraints,
    )
}
