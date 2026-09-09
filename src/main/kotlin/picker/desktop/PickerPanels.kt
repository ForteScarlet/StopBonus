package picker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import picker.core.DateConstraints
import picker.core.DateTimeConstraints
import picker.core.ParseResult
import picker.core.PickerCodecs
import picker.core.PickerEnvironment
import picker.core.PickerIssue
import picker.core.PickerIssueCodes
import picker.core.PickerTextCodec
import picker.core.PickerStrings
import picker.core.TimeFormatOptions
import picker.foundation.BasicCalendar
import picker.foundation.BasicTimeEditor
import picker.foundation.CalendarNavigationState
import picker.foundation.DateDraft
import picker.foundation.DateTimeDraft
import picker.foundation.TimeDraft
import picker.foundation.TimeEditorScope
import picker.foundation.resolveDraft
import picker.foundation.rememberCalendarNavigationState
import picker.foundation.textFieldValue
import java.time.LocalDate

/**
 * 渲染可内嵌或由调用方会话持有的独立日期面板。
 */
@Composable
fun DatePickerPanel(
    /**
     * 当前选中的日期。
     */
    value: LocalDate?,
    /**
     * 接收选中的日期；清空时为 null。
     */
    onValueChange: (LocalDate?) -> Unit,
    /**
     * 日历文案及“今天”行为使用的环境。
     */
    environment: PickerEnvironment,
    /**
     * 应用于面板表面的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 跨重组保留的日历导航状态。
     */
    navigationState: CalendarNavigationState = rememberCalendarNavigationState(),
    /**
     * 日期闭区间与附加校验规则。
     */
    constraints: DateConstraints = DateConstraints(),
    /**
     * 视觉样式；默认从最近的 [PickerTheme] 取得。
     */
    style: PickerStyle = PickerTheme.style,
    /**
     * 可替换的日期格、标题及操作槽位。
     */
    slots: DatePickerSlots = DatePickerDefaults.slots(),
) {
    val activeStyle = effectiveStyle(style)
    PickerPanelSurface(
        modifier = modifier,
        style = activeStyle,
        preferredWidth = activeStyle.dimensions.panelPreferredWidth,
    ) {
        BasicCalendar(
            value = value,
            onValueChange = onValueChange,
            environment = environment,
            navigationState = navigationState,
            constraints = constraints,
            selectedColor = activeStyle.colors.selected,
            selectedContentColor = activeStyle.colors.onSelected,
            todayColor = activeStyle.colors.hoverSurface,
            focusColor = activeStyle.colors.focus,
            onSurfaceColor = activeStyle.colors.onSurface,
            disabledContentColor = activeStyle.colors.disabled,
            outsideMonthColor = activeStyle.colors.onSurface.copy(alpha = 0.55f),
            cellMinSize = activeStyle.dimensions.cellMinSize,
            dayContent = slots.dayContent,
            header = slots.header,
        )
    }
}

/**
 * 渲染可内嵌或由调用方会话持有的独立时间面板。
 */
@Composable
fun TimePickerPanel(
    /**
     * 当前分段时间草稿。
     */
    draft: TimeDraft,
    /**
     * 接收每次草稿变化。
     */
    onDraftChange: (TimeDraft) -> Unit,
    /**
     * 小时制与可见精度。
     */
    format: TimeFormatOptions,
    /**
     * 应用于面板表面的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 控件是否接受输入。
     */
    enabled: Boolean = true,
    /**
     * 控件是否可见但不可编辑。
     */
    readOnly: Boolean = false,
    /**
     * 视觉样式；默认从最近的 [PickerTheme] 取得。
     */
    style: PickerStyle = PickerTheme.style,
    /**
     * 可替换的时间编辑器及操作槽位。
     */
    slots: TimePickerSlots = TimePickerDefaults.slots(),
) {
    val activeStyle = effectiveStyle(style)
    PickerPanelSurface(
        modifier = modifier,
        style = activeStyle,
        preferredWidth = activeStyle.dimensions.timePanelPreferredWidth,
    ) {
        DefaultTimeEditorContent(
            draft = draft,
            onDraftChange = onDraftChange,
            format = format,
            enabled = enabled,
            readOnly = readOnly,
            style = activeStyle,
            timeContent = slots.timeContent,
        )
    }
}

/**
 * 渲染可内嵌或由调用方会话持有的独立本地日期时间面板。
 */
@Composable
fun DateTimePickerPanel(
    /**
     * 当前组合日期时间草稿。
     */
    draft: DateTimeDraft,
    /**
     * 接收每次草稿变化。
     */
    onDraftChange: (DateTimeDraft) -> Unit,
    /**
     * 文案、日历行为及当前日期使用的环境。
     */
    environment: PickerEnvironment,
    /**
     * 时间部分使用的小时制与可见精度。
     */
    format: TimeFormatOptions,
    /**
     * 应用于面板表面的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 跨重组保留的日历导航状态。
     */
    navigationState: CalendarNavigationState = rememberCalendarNavigationState(),
    /**
     * 日期、时间及组合本地值的约束。
     */
    constraints: DateTimeConstraints = DateTimeConstraints(),
    /**
     * 控件是否接受输入。
     */
    enabled: Boolean = true,
    /**
     * 控件是否可见但不可编辑。
     */
    readOnly: Boolean = false,
    /**
     * 视觉样式；默认从最近的 [PickerTheme] 取得。
     */
    style: PickerStyle = PickerTheme.style,
    /**
     * 可替换的日历、时间编辑器及操作槽位。
     */
    slots: DateTimePickerSlots = DateTimePickerDefaults.slots(),
) {
    val activeStyle = effectiveStyle(style)
    PickerPanelSurface(
        modifier = modifier,
        style = activeStyle,
        preferredWidth = activeStyle.dimensions.panelPreferredWidth,
    ) {
        DateTimeDraftEditorContent(
            draft = draft,
            onDraftChange = onDraftChange,
            environment = environment,
            format = format,
            navigationState = navigationState,
            constraints = constraints,
            enabled = enabled,
            readOnly = readOnly,
            style = activeStyle,
            slots = slots,
        )
    }
}

/**
 * 渲染字段持有面板中的日期部分，并处理原始文本模式。
 */
@Composable
internal fun DatePickerDraftEditorContent(
    draft: DateDraft,
    onDraftChange: (DateDraft) -> Unit,
    environment: PickerEnvironment,
    navigationState: CalendarNavigationState,
    constraints: DateConstraints,
    enabled: Boolean,
    readOnly: Boolean,
    style: PickerStyle,
    slots: DatePickerSlots,
    codec: PickerTextCodec<LocalDate> = PickerCodecs.date(),
    onSubmit: () -> Unit = {},
    rawInput: TextFieldValue? = null,
    onRawInputChange: ((TextFieldValue) -> Unit)? = null,
    rawIssue: PickerIssue? = null,
    strings: PickerStrings = PickerStrings.forLocale(environment.locale),
) {
    val dateResult = resolveDraft(draft, codec, environment.locale)
    val selectedDate = (dateResult as? picker.core.ParseResult.Parsed)?.value
    Column(verticalArrangement = Arrangement.spacedBy(style.dimensions.sectionGap)) {
        if (rawInput != null) {
            PickerRawInput(
                input = rawInput,
                onInputChange = onRawInputChange ?: {},
                hint = codec.formatHint,
                style = style,
                enabled = enabled,
                readOnly = readOnly,
                issue = rawIssue,
                strings = strings,
                onSubmit = onSubmit,
            )
        }
        BasicCalendar(
            value = selectedDate,
            onValueChange = { date ->
                onDraftChange(
                    draft.copy(
                        input = date?.let {
                            textFieldValue(codec.format(it, environment.locale))
                        } ?: TextFieldValue(),
                    )
                )
            },
            environment = environment,
            navigationState = navigationState,
            constraints = constraints,
            selectedColor = style.colors.selected,
            selectedContentColor = style.colors.onSelected,
            todayColor = style.colors.hoverSurface,
            focusColor = style.colors.focus,
            onSurfaceColor = style.colors.onSurface,
            disabledContentColor = style.colors.disabled,
            outsideMonthColor = style.colors.onSurface.copy(alpha = 0.55f),
            cellMinSize = style.dimensions.cellMinSize,
            enabled = enabled,
            readOnly = readOnly,
            dayContent = slots.dayContent,
            header = slots.header,
        )
    }
}

/**
 * 渲染组合面板，并保留整体值尚未完成或无效时的原始输入。
 */
@Composable
internal fun DateTimeDraftEditorContent(
    draft: DateTimeDraft,
    onDraftChange: (DateTimeDraft) -> Unit,
    environment: PickerEnvironment,
    format: TimeFormatOptions,
    codec: PickerTextCodec<java.time.LocalDateTime> = PickerCodecs.dateTime(format),
    navigationState: CalendarNavigationState,
    constraints: DateTimeConstraints,
    enabled: Boolean,
    readOnly: Boolean,
    style: PickerStyle,
    slots: DateTimePickerSlots,
    onSubmit: () -> Unit = {},
    rawInput: TextFieldValue? = null,
    onRawInputChange: ((TextFieldValue) -> Unit)? = null,
    rawIssue: PickerIssue? = null,
    strings: PickerStrings = PickerStrings.forLocale(environment.locale),
) {
    // 日期时间文本未完成时不能可靠地拆分成分段，须保留为整体输入直至解析成功。
    if (rawInput != null) {
        PickerRawInput(
            input = rawInput,
            onInputChange = onRawInputChange ?: {},
            hint = codec.formatHint,
            style = style,
            enabled = enabled,
            readOnly = readOnly,
            issue = rawIssue,
            strings = strings,
            onSubmit = onSubmit,
        )
        return
    }
    val dateResult = resolveDraft(draft.date, PickerCodecs.date(), environment.locale)
    val selectedDate = (dateResult as? picker.core.ParseResult.Parsed)?.value
    Row(horizontalArrangement = Arrangement.spacedBy(style.dimensions.sectionGap)) {
        BasicCalendar(
            value = selectedDate,
            onValueChange = { date ->
                onDraftChange(
                    draft.copy(
                        date = DateDraft(
                            date?.let { textFieldValue(PickerCodecs.date().format(it, environment.locale)) }
                                ?: TextFieldValue(),
                        )
                    )
                )
            },
            environment = environment,
            navigationState = navigationState,
            constraints = constraints.dates,
            selectedColor = style.colors.selected,
            selectedContentColor = style.colors.onSelected,
            todayColor = style.colors.hoverSurface,
            focusColor = style.colors.focus,
            onSurfaceColor = style.colors.onSurface,
            disabledContentColor = style.colors.disabled,
            outsideMonthColor = style.colors.onSurface.copy(alpha = 0.55f),
            cellMinSize = style.dimensions.cellMinSize,
            enabled = enabled,
            readOnly = readOnly,
            dayContent = slots.dayContent,
            header = slots.header,
            modifier = Modifier.weight(1f),
        )
        DefaultTimeEditorContent(
            draft = draft.time,
            onDraftChange = { onDraftChange(draft.copy(time = it)) },
            format = format,
            enabled = enabled,
            readOnly = readOnly,
            style = style,
            timeContent = slots.timeContent,
            onSubmit = onSubmit,
        )
    }
}

/**
 * 渲染面板文本未完成时临时使用的整体输入框。
 */
@Composable
internal fun PickerRawInput(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    hint: String,
    style: PickerStyle,
    enabled: Boolean,
    readOnly: Boolean,
    issue: PickerIssue?,
    strings: PickerStrings,
    onSubmit: () -> Unit,
) {
    // 不在每次输入后规范化；原始 TextFieldValue 须持有光标与输入法组合状态直至解析完成。
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .border(style.dimensions.borderWidth, style.colors.outline, style.shapes.control)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .onPreviewKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.Enter &&
                        enabled &&
                        !readOnly &&
                        input.composition == null
                    ) {
                        onSubmit()
                        true
                    } else {
                        false
                    }
                },
            enabled = enabled,
            readOnly = readOnly,
            singleLine = true,
            textStyle = style.typography.body,
            decorationBox = { inner ->
                Box {
                    if (input.text.isEmpty()) {
                        BasicText(hint, style = style.typography.caption)
                    }
                    inner()
                }
            },
        )
        issue?.let {
            BasicText(
                text = strings.message(it),
                style = style.typography.caption.copy(color = style.colors.error),
            )
        }
    }
}

/**
 * 将解析结果转换为面板需要显示的问题；无问题时返回 null。
 */
internal fun parseIssue(result: ParseResult<*>): PickerIssue? = when (result) {
    ParseResult.Empty -> null
    ParseResult.Incomplete -> PickerIssue(PickerIssueCodes.Incomplete)
    is ParseResult.Invalid -> result.issue
    is ParseResult.Parsed -> null
}

/**
 * 应用当前样式并渲染自定义时间编辑器槽位。
 */
@Composable
internal fun DefaultTimeEditorContent(
    draft: TimeDraft,
    onDraftChange: (TimeDraft) -> Unit,
    format: TimeFormatOptions,
    enabled: Boolean,
    readOnly: Boolean,
    style: PickerStyle,
    timeContent: @Composable TimeEditorScope.() -> Unit,
    onSubmit: () -> Unit = {},
) {
    PickerTheme(style = style) {
        BasicTimeEditor(
            draft = draft,
            onDraftChange = onDraftChange,
            format = format,
            enabled = enabled,
            readOnly = readOnly,
            onSubmit = onSubmit,
        ) {
            timeContent()
        }
    }
}

@Composable
private fun PickerPanelSurface(
    modifier: Modifier,
    style: PickerStyle,
    preferredWidth: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    PickerTheme(style = style) {
        Box(
            modifier = modifier
                .widthIn(min = preferredWidth)
                .shadow(style.dimensions.panelElevation, style.shapes.panel)
                .background(style.colors.surface, style.shapes.panel)
                .border(style.dimensions.borderWidth, style.colors.outline, style.shapes.panel)
                .padding(style.dimensions.panelPadding),
        ) {
            content()
        }
    }
}
