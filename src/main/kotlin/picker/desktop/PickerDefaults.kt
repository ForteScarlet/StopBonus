package picker.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import picker.foundation.CalendarDayPresentation
import picker.foundation.CalendarHeaderScope
import picker.foundation.TimeEditorScope
import picker.foundation.TimeSegment
import picker.foundation.PeriodToggle
import picker.core.TimePrecision
import kotlin.math.abs

/**
 * 暴露给选择器面板自定义操作行槽位的动作。
 *
 * 能力标记由字段状态计算；对应标记为 false 时不得调用回调。
 */
class PickerActionsScope internal constructor(
    /**
     * 当前草稿能否应用。
     */
    val canApply: Boolean,
    /**
     * 是否允许清空当前值。
     */
    val canClear: Boolean,
    /**
     * 当前值能否替换为“现在”。
     */
    val canUseNow: Boolean,
    /**
     * 当前值能否替换为“今天”。
     */
    val canUseToday: Boolean = canUseNow,
    /**
     * 应用当前面板草稿。
     */
    val apply: () -> Unit,
    /**
     * 取消面板并恢复打开时的快照。
     */
    val cancel: () -> Unit,
    /**
     * 清空当前面板值。
     */
    val clear: () -> Unit,
    /**
     * 将当前值替换为当前本地时间/日期时间。
     */
    val now: () -> Unit,
    /**
     * 将当前日期部分替换为当前本地日期。
     */
    val today: () -> Unit,
)

/**
 * 日期选择器面板的可定制槽位。
 */
class DatePickerSlots(
    /**
     * 每个日期格的内容。
     */
    val dayContent: @Composable (CalendarDayPresentation) -> Unit,
    /**
     * 月份导航标题内容。
     */
    val header: @Composable (CalendarHeaderScope) -> Unit,
    /**
     * 面板操作行内容。
     */
    val actions: @Composable PickerActionsScope.() -> Unit,
)

/**
 * 时间选择器面板的可定制槽位。
 */
class TimePickerSlots(
    /**
     * 分段时间编辑器内容。
     */
    val timeContent: @Composable TimeEditorScope.() -> Unit,
    /**
     * 面板操作行内容。
     */
    val actions: @Composable PickerActionsScope.() -> Unit,
)

/**
 * 日期时间选择器面板的可定制槽位。
 */
class DateTimePickerSlots(
    /**
     * 每个日期格的内容。
     */
    val dayContent: @Composable (CalendarDayPresentation) -> Unit,
    /**
     * 月份导航标题内容。
     */
    val header: @Composable (CalendarHeaderScope) -> Unit,
    /**
     * 分段时间编辑器内容。
     */
    val timeContent: @Composable TimeEditorScope.() -> Unit,
    /**
     * 面板操作行内容。
     */
    val actions: @Composable PickerActionsScope.() -> Unit,
)

/**
 * 日期选择器的默认槽位与槽位工厂。
 */
object DatePickerDefaults {
    private val defaultDayContent: @Composable (CalendarDayPresentation) -> Unit = { presentation ->
        DefaultPickerDay(presentation)
    }
    private val defaultHeader: @Composable (CalendarHeaderScope) -> Unit = { scope ->
        DefaultPickerHeader(scope)
    }
    private val defaultActions: @Composable PickerActionsScope.() -> Unit = {}
    private val defaultSlots = DatePickerSlots(defaultDayContent, defaultHeader, defaultActions)

    /**
     * 创建日期选择器槽位；未替换时复用默认单例。
     */
    fun slots(
        /**
         * 可选的日期格内容替换。
         */
        dayContent: @Composable (CalendarDayPresentation) -> Unit = defaultDayContent,
        /**
         * 可选的日历标题替换。
         */
        header: @Composable (CalendarHeaderScope) -> Unit = defaultHeader,
        /**
         * 可选的操作行替换。
         */
        actions: @Composable PickerActionsScope.() -> Unit = defaultActions,
    ): DatePickerSlots {
        // 稳定的默认槽位身份避免每次重组分配新对象，单个槽位仍可自由替换。
        if (dayContent === defaultDayContent && header === defaultHeader && actions === defaultActions) {
            return defaultSlots
        }
        return DatePickerSlots(dayContent, header, actions)
    }
}

/**
 * 时间选择器的默认槽位与槽位工厂。
 */
object TimePickerDefaults {
    private val defaultTimeContent: @Composable TimeEditorScope.() -> Unit = {
        DefaultTimePickerContent(this)
    }
    private val defaultActions: @Composable PickerActionsScope.() -> Unit = {}
    private val defaultSlots = TimePickerSlots(defaultTimeContent, defaultActions)

    /**
     * 创建时间选择器槽位；未替换时复用默认单例。
     */
    fun slots(
        /**
         * 可选的时间编辑器内容替换。
         */
        timeContent: @Composable TimeEditorScope.() -> Unit = defaultTimeContent,
        /**
         * 可选的操作行替换。
         */
        actions: @Composable PickerActionsScope.() -> Unit = defaultActions,
    ): TimePickerSlots {
        // 调用方未传任何定制参数时，保持默认槽位包的引用稳定。
        if (timeContent === defaultTimeContent && actions === defaultActions) {
            return defaultSlots
        }
        return TimePickerSlots(timeContent, actions)
    }
}

/**
 * 日期时间选择器的默认槽位与槽位工厂。
 */
object DateTimePickerDefaults {
    private val defaultDayContent: @Composable (CalendarDayPresentation) -> Unit = { presentation ->
        DefaultPickerDay(presentation)
    }
    private val defaultHeader: @Composable (CalendarHeaderScope) -> Unit = { scope ->
        DefaultPickerHeader(scope)
    }
    private val defaultTimeContent: @Composable TimeEditorScope.() -> Unit = {
        DefaultTimePickerContent(this)
    }
    private val defaultActions: @Composable PickerActionsScope.() -> Unit = {}
    private val defaultSlots = DateTimePickerSlots(
        defaultDayContent,
        defaultHeader,
        defaultTimeContent,
        defaultActions,
    )

    /**
     * 创建日期时间槽位；未替换时复用默认单例。
     */
    fun slots(
        /**
         * 可选的日期格内容替换。
         */
        dayContent: @Composable (CalendarDayPresentation) -> Unit = defaultDayContent,
        /**
         * 可选的日历标题替换。
         */
        header: @Composable (CalendarHeaderScope) -> Unit = defaultHeader,
        /**
         * 可选的时间编辑器内容替换。
         */
        timeContent: @Composable TimeEditorScope.() -> Unit = defaultTimeContent,
        /**
         * 可选的操作行替换。
         */
        actions: @Composable PickerActionsScope.() -> Unit = defaultActions,
    ): DateTimePickerSlots {
        // 槽位身份仅用于分配缓存；任一函数被替换时仍完全由调用方控制其行为。
        if (
            dayContent === defaultDayContent &&
            header === defaultHeader &&
            timeContent === defaultTimeContent &&
            actions === defaultActions
        ) {
            return defaultSlots
        }
        return DateTimePickerSlots(dayContent, header, timeContent, actions)
    }
}

@Composable
private fun DefaultTimePickerContent(editor: TimeEditorScope) {
    val style = PickerTheme.current()
    var dialMode by remember { mutableStateOf(TimeDialMode.Hour) }
    val currentHour = editor.currentHour24()
    val currentMinute = editor.value(TimeSegment.Minute).text.toIntOrNull()
    val currentSecond = editor.value(TimeSegment.Second).text.toIntOrNull()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(style.dimensions.controlGap),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeWheel(
                editor = editor,
                segment = TimeSegment.Hour,
                label = "选择小时",
                selected = dialMode == TimeDialMode.Hour,
                onSelectDialMode = { dialMode = TimeDialMode.Hour },
                style = style,
            )
            BasicText(":", style = style.typography.body)
            TimeWheel(
                editor = editor,
                segment = TimeSegment.Minute,
                label = "选择分钟",
                selected = dialMode == TimeDialMode.Minute,
                onSelectDialMode = { dialMode = TimeDialMode.Minute },
                style = style,
            )
            if (editor.format.precision == TimePrecision.Second) {
                BasicText(":", style = style.typography.body)
                TimeWheel(
                    editor = editor,
                segment = TimeSegment.Second,
                label = "选择秒",
                    selected = dialMode == TimeDialMode.Second,
                    onSelectDialMode = { dialMode = TimeDialMode.Second },
                    style = style,
                )
            }
            editor.PeriodToggle(
                enabled = editor.enabled,
                readOnly = editor.readOnly,
                selectedBorderColor = style.colors.focus,
                borderColor = style.colors.outline,
                selectedContentColor = style.colors.selected,
                contentColor = style.colors.onSurface,
            )
        }
        AnimatedContent(
            targetState = dialMode,
            transitionSpec = {
                (fadeIn(animationSpec = tween(180)) + slideInVertically(
                    animationSpec = tween(180),
                    initialOffsetY = { it / 8 },
                )).togetherWith(
                    fadeOut(animationSpec = tween(100)) + slideOutVertically(
                        animationSpec = tween(100),
                        targetOffsetY = { -it / 8 },
                    ),
                )
            },
            label = "时间表盘模式切换",
        ) { mode ->
            TimeDial(
                mode = mode,
                hour24 = currentHour,
                minute = currentMinute,
                second = currentSecond,
                hourCycle = editor.format.hourCycle,
                enabled = editor.enabled && !editor.readOnly,
                onHourSelected = { hour ->
                    if (editor.format.hourCycle == picker.core.HourCycle.H12) {
                        editor.setNumber(TimeSegment.Hour, if (hour == 0) 12 else hour)
                    } else {
                        editor.setNumber(TimeSegment.Hour, hour)
                    }
                    dialMode = TimeDialMode.Minute
                },
                onMinuteSelected = { minute -> editor.setNumber(TimeSegment.Minute, minute) },
                onSecondSelected = { second -> editor.setNumber(TimeSegment.Second, second) },
                onValueCleared = { selectedMode ->
                    editor.clearNumber(selectedMode.segment)
                },
                onIncrement = { selectedMode, amount ->
                    editor.cycle(selectedMode.segment, amount)
                },
                style = style,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun TimeWheel(
    editor: TimeEditorScope,
    segment: TimeSegment,
    label: String,
    selected: Boolean,
    onSelectDialMode: (() -> Unit)?,
    style: PickerStyle,
) {
    var dragDistance by remember(segment) { mutableStateOf(0f) }
    var scrollDirection by remember(segment) { mutableStateOf(1) }
    val latestEditor by rememberUpdatedState(editor)
    val enabled = editor.enabled && !editor.readOnly
    val displayedValue = editor.value(segment).text.toIntOrNull()
        ?.toString()
        ?.padStart(2, '0')
        ?: "--"

    /**
     * 以相同的状态通道处理滚轮和拖拽，表盘会随草稿重组同步。
     */
    fun move(amount: Int) {
        if (!enabled) return
        scrollDirection = amount
        latestEditor.cycle(segment, amount)
    }

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 52.dp, minHeight = 48.dp)
            .background(
                color = if (selected) style.colors.selected else style.colors.mutedSurface,
                shape = style.shapes.control,
            )
            .border(
                width = style.dimensions.borderWidth,
                color = if (selected) style.colors.selected else style.colors.outline,
                shape = style.shapes.control,
            )
            .clickable(enabled = enabled && onSelectDialMode != null) {
                onSelectDialMode?.invoke()
            }
            .pointerInput(segment, enabled) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (!enabled) return@detectVerticalDragGestures
                    dragDistance += dragAmount
                    while (abs(dragDistance) >= 18f) {
                        val amount = if (dragDistance < 0f) 1 else -1
                        move(amount)
                        dragDistance -= if (dragDistance < 0f) -18f else 18f
                    }
                }
            }
            .focusable(enabled = enabled)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionUp -> {
                        move(-1)
                        true
                    }

                    Key.DirectionDown -> {
                        move(1)
                        true
                    }

                    else -> false
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                // 向下滚，数值+1
                if (delta != 0f) move(if (delta < 0f) -1 else 1)
            }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (enabled && event.buttons.isPressed(PointerButton.Tertiary.index)) {
                    latestEditor.clearNumber(segment)
                }
            }
            .semantics {
                contentDescription = label
                role = Role.Button
                stateDescription = displayedValue
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = displayedValue,
            transitionSpec = {
                val incomingOffset = if (scrollDirection > 0) 1 else -1
                (slideInVertically(animationSpec = tween(140)) { it * incomingOffset } +
                    fadeIn(animationSpec = tween(140))).togetherWith(
                    slideOutVertically(animationSpec = tween(140)) { -it * incomingOffset } +
                        fadeOut(animationSpec = tween(100)),
                )
            },
            label = "$label 数字滚动",
        ) { text ->
            BasicText(
                text = text,
                style = style.typography.body.copy(
                    color = if (selected) style.colors.onSelected else style.colors.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun DefaultPickerDay(presentation: CalendarDayPresentation) {
    val style = PickerTheme.current()
    BasicText(
        text = presentation.date.dayOfMonth.toString(),
        style = style.typography.body.copy(color = presentation.contentColor),
    )
}

@Composable
private fun DefaultPickerHeader(scope: CalendarHeaderScope) {
    val style = PickerTheme.current()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PickerIcon(
            kind = PickerIconKind.ChevronLeft,
            contentDescription = "上一个月",
            enabled = scope.canGoPrevious,
            onClick = scope.previous,
            style = style,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionText(
                scope.displayedMonth.year.toString() + "年",
                true,
                scope.showYears,
                "选择年份",
                style,
            )
            ActionText(
                scope.displayedMonth.monthValue.toString() + "月",
                true,
                scope.showMonths,
                "选择月份",
                style,
            )
        }
        PickerIcon(
            kind = PickerIconKind.ChevronRight,
            contentDescription = "下一个月",
            enabled = scope.canGoNext,
            onClick = scope.next,
            style = style,
        )
    }
}

@Composable
private fun ActionText(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String = text,
    style: PickerStyle = PickerTheme.current(),
    testTag: String? = null,
) {
    BasicText(
        text = text,
        modifier = Modifier
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                if (!enabled) disabled()
                testTag?.let { this.testTag = it }
            }
            .padding(horizontal = 4.dp, vertical = 5.dp),
        style = style.typography.body,
    )
}
