package picker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import picker.core.DateConstraints
import picker.core.DateTimeConstraints
import picker.core.ParseResult
import picker.core.PickerCodecs
import picker.core.PickerEnvironment
import picker.core.PickerIssueCodes
import picker.core.PickerNow
import picker.core.PickerStrings
import picker.core.TimeFormatOptions
import picker.core.ValidationResult
import picker.core.hiddenPrecisionDescription
import picker.core.parsePickerText
import picker.core.sampleNow
import picker.core.truncateForPrecision
import picker.core.visibleTimeChanged
import picker.foundation.CalendarNavigationState
import picker.foundation.CalendarView
import picker.foundation.DateDraft
import picker.foundation.DateTimeDraft
import picker.foundation.FieldConfiguration
import picker.foundation.PickerFieldState
import picker.foundation.PickerPopup
import picker.foundation.PickerSemantics
import picker.foundation.SubmitEvaluation
import picker.foundation.TimeDraft
import picker.foundation.hasComposition
import picker.foundation.rememberPickerPopupPositionProvider
import picker.foundation.resolveDraft
import picker.foundation.rememberCalendarNavigationState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 渲染同时支持直接文本编辑与事务式日历弹层的日期字段。
 */
@Composable
fun DatePickerField(
    /**
     * 外部已提交的日期；字段为空时为 null。
     */
    value: LocalDate?,
    /**
     * 所有者接受候选值后接收该值。
     */
    onValueChange: (LocalDate?) -> Unit,
    /**
     * 时钟、语言环境、每周起始日与时区配置。
     */
    environment: PickerEnvironment,
    /**
     * 应用于字段外壳的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 跨重组与弹层会话保留的状态。
     */
    state: PickerFieldState<LocalDate> = rememberDatePickerFieldState(),
    /**
     * 可选日期闭区间与附加校验。
     */
    constraints: DateConstraints = DateConstraints(),
    /**
     * 提交时是否拒绝 null。
     */
    required: Boolean = false,
    /**
     * 字段与弹层是否接受输入。
     */
    enabled: Boolean = true,
    /**
     * 字段是否可见但不可编辑。
     */
    readOnly: Boolean = false,
    /**
     * 直接输入与面板输入使用的文本解析器和格式化器。
     */
    codec: picker.core.PickerTextCodec<LocalDate> = PickerCodecs.date(),
    /**
     * 视觉样式；默认从最近的 [PickerTheme] 取得。
     */
    style: PickerStyle = PickerTheme.style,
    /**
     * 本地化的问题消息与操作标签。
     */
    strings: PickerStrings = PickerStrings.forLocale(environment.locale),
    /**
     * 渲染在字段上方的可选标签。
     */
    label: (@Composable () -> Unit)? = null,
    /**
     * 渲染在校验文字下方的可选辅助内容。
     */
    supportingText: (@Composable () -> Unit)? = null,
    /**
     * 可定制的日历与操作行槽位。
     */
    slots: DatePickerSlots = DatePickerDefaults.slots(),
) {
    val owner = remember { Any() }
    val activeStyle = effectiveStyle(style)
    val navigationState = rememberCalendarNavigationState()
    state.bind(
        owner = owner,
        config = dateConfiguration(
            value = value,
            environment = environment,
            constraints = constraints,
            required = required,
            codec = codec,
        ),
        onValueChange = onValueChange,
    )
    DisposableEffect(state, owner) {
        onDispose { state.unbind(owner) }
    }

    PickerFieldFrame(
        state = state,
        environment = environment,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        style = activeStyle,
        strings = strings,
        label = label,
        supportingText = supportingText,
        fieldHint = codec.formatHint,
        onOpen = {
            navigationState.prepareForOpening()
            state.openPanel()
        },
        onEscape = {
            if (navigationState.view != CalendarView.Days) {
                navigationState.showDays()
            } else {
                state.closePanelWithoutApplying()
            }
        },
        onApply = { state.applyPanel(environment.sampleNow()) },
        panel = {
            DatePickerDraftEditorContent(
                state = state,
                environment = environment,
                navigationState = navigationState,
                constraints = constraints,
                style = activeStyle,
                slots = slots,
                enabled = enabled,
                readOnly = readOnly,
                codec = codec,
                onSubmit = { state.applyPanel(environment.sampleNow()) },
                strings = strings,
            )
        },
        actions = {
            DatePickerActions(
                state = state,
                environment = environment,
                constraints = constraints,
                codec = codec,
                slots = slots,
            )
        },
    )
}

/**
 * 渲染同时支持直接文本编辑与事务式分段时间弹层的时间字段。
 */
@Composable
fun TimePickerField(
    /**
     * 外部已提交的时间；字段为空时为 null。
     */
    value: LocalTime?,
    /**
     * 所有者接受候选值后接收该值。
     */
    onValueChange: (LocalTime?) -> Unit,
    /**
     * 时钟、语言环境、周起始日及显示时区配置。
     */
    environment: PickerEnvironment,
    /**
     * 应用于字段外壳的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 跨重组与弹层会话保留的状态。
     */
    state: PickerFieldState<LocalTime> = rememberTimePickerFieldState(),
    /**
     * 文本与分段编辑器使用的小时制和可见精度。
     */
    format: TimeFormatOptions = TimeFormatOptions(),
    /**
     * 可选时间闭区间及附加校验。
     */
    constraints: picker.core.TimeConstraints = picker.core.TimeConstraints(),
    /**
     * 提交时是否拒绝 null。
     */
    required: Boolean = false,
    /**
     * 字段与弹层是否接受输入。
     */
    enabled: Boolean = true,
    /**
     * 字段是否仅可查看而不可编辑。
     */
    readOnly: Boolean = false,
    /**
     * 直接输入与面板输入共用的文本编解码器。
     */
    codec: picker.core.PickerTextCodec<LocalTime> = PickerCodecs.time(format),
    /**
     * 视觉样式；默认从最近的 [PickerTheme] 取得。
     */
    style: PickerStyle = PickerTheme.style,
    /**
     * 本地化问题文案与操作标签。
     */
    strings: PickerStrings = PickerStrings.forLocale(environment.locale),
    /**
     * 可选的字段上方标签。
     */
    label: (@Composable () -> Unit)? = null,
    /**
     * 可选的校验文案下方辅助内容。
     */
    supportingText: (@Composable () -> Unit)? = null,
    /**
     * 可自定义的时间编辑器与操作行槽位。
     */
    slots: TimePickerSlots = TimePickerDefaults.slots(),
) {
    val owner = remember { Any() }
    val activeStyle = effectiveStyle(style)
    state.bind(
        owner = owner,
        config = timeConfiguration(
            value = value,
            environment = environment,
            format = format,
            constraints = constraints,
            required = required,
            codec = codec,
        ),
        onValueChange = onValueChange,
    )
    DisposableEffect(state, owner) {
        onDispose { state.unbind(owner) }
    }

    PickerFieldFrame(
        state = state,
        environment = environment,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        style = activeStyle,
        strings = strings,
        label = label,
        supportingText = supportingText,
        fieldHint = codec.formatHint,
        onOpen = state::openPanel,
        onApply = { state.applyPanel(environment.sampleNow()) },
        hiddenPrecision = if (value != null && hiddenPrecisionDescription(value, format)) {
            strings.hiddenPrecision
        } else {
            null
        },
        panel = {
            val rawInput = state.panelRawInput
            if (rawInput != null) {
                PickerRawInput(
                    input = rawInput,
                    onInputChange = { next ->
                        if (next.composition != null) {
                            state.updatePanelRawInput(next)
                        } else if (next.text.isEmpty()) {
                            state.clearPanel()
                        } else {
                            when (val result = parsePickerText(codec, next.text, environment.locale)) {
                                is ParseResult.Parsed -> state.replacePanelRawInput(
                                    TimeDraft.from(
                                        result.value,
                                        format
                                    )
                                )

                                else -> state.updatePanelRawInput(next)
                            }
                        }
                    },
                    hint = codec.formatHint,
                    style = activeStyle,
                    enabled = enabled,
                    readOnly = readOnly,
                    issue = parseIssue(parsePickerText(codec, rawInput.text, environment.locale)),
                    strings = strings,
                    onSubmit = { state.applyPanel(environment.sampleNow()) },
                )
            } else {
                val draft = state.panelDraft as? TimeDraft ?: TimeDraft()
                DefaultTimeEditorContent(
                    draft = draft,
                    onDraftChange = { next ->
                        state.updatePanelDraft(next, edited = timeDraftContentChanged(draft, next))
                    },
                    format = format,
                    enabled = enabled,
                    readOnly = readOnly,
                    style = activeStyle,
                    timeContent = slots.timeContent,
                    onSubmit = { state.applyPanel(environment.sampleNow()) },
                )
            }
        },
        actions = {
            TimePickerActions(
                state = state,
                environment = environment,
                format = format,
                constraints = constraints,
                slots = slots,
            )
        },
    )
}

/**
 * 渲染带日历/时间弹层及事务式提交语义的本地日期时间字段。
 */
@Composable
fun DateTimePickerField(
    /**
     * 外部已提交的本地日期时间；字段为空时为 null。
     */
    value: LocalDateTime?,
    /**
     * 所有者接受候选值后接收该值。
     */
    onValueChange: (LocalDateTime?) -> Unit,
    /**
     * 时钟、语言环境、周起始日及显示时区配置。
     */
    environment: PickerEnvironment,
    /**
     * 应用于字段外壳的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 跨重组与弹层会话保留的状态。
     */
    state: PickerFieldState<LocalDateTime> = rememberDateTimePickerFieldState(),
    /**
     * 时间部分使用的小时制与可见精度。
     */
    format: TimeFormatOptions = TimeFormatOptions(),
    /**
     * 日期、时间及组合值的约束。
     */
    constraints: DateTimeConstraints = DateTimeConstraints(),
    /**
     * 提交时是否拒绝 null。
     */
    required: Boolean = false,
    /**
     * 字段与弹层是否接受输入。
     */
    enabled: Boolean = true,
    /**
     * 字段是否仅可查看而不可编辑。
     */
    readOnly: Boolean = false,
    /**
     * 直接输入与面板输入共用的文本编解码器。
     */
    codec: picker.core.PickerTextCodec<LocalDateTime> = PickerCodecs.dateTime(format),
    /**
     * 视觉样式；默认从最近的 [PickerTheme] 取得。
     */
    style: PickerStyle = PickerTheme.style,
    /**
     * 本地化问题文案与操作标签。
     */
    strings: PickerStrings = PickerStrings.forLocale(environment.locale),
    /**
     * 可选的字段上方标签。
     */
    label: (@Composable () -> Unit)? = null,
    /**
     * 可选的校验文案下方辅助内容。
     */
    supportingText: (@Composable () -> Unit)? = null,
    /**
     * 可自定义的日历、时间编辑器及操作行槽位。
     */
    slots: DateTimePickerSlots = DateTimePickerDefaults.slots(),
) {
    val owner = remember { Any() }
    val activeStyle = effectiveStyle(style)
    val navigationState = rememberCalendarNavigationState()
    state.bind(
        owner = owner,
        config = dateTimeConfiguration(
            value = value,
            environment = environment,
            format = format,
            constraints = constraints,
            required = required,
            codec = codec,
        ),
        onValueChange = onValueChange,
    )
    DisposableEffect(state, owner) {
        onDispose { state.unbind(owner) }
    }

    PickerFieldFrame(
        state = state,
        environment = environment,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        style = activeStyle,
        strings = strings,
        label = label,
        supportingText = supportingText,
        fieldHint = codec.formatHint,
        onOpen = {
            navigationState.prepareForOpening()
            state.openPanel()
        },
        onEscape = {
            if (navigationState.view != CalendarView.Days) {
                navigationState.showDays()
            } else {
                state.closePanelWithoutApplying()
            }
        },
        onApply = { state.applyPanel(environment.sampleNow()) },
        hiddenPrecision = if (value != null && hiddenPrecisionDescription(value.toLocalTime(), format)) {
            strings.hiddenPrecision
        } else {
            null
        },
        panel = {
            val rawInput = state.panelRawInput
            val draft = state.panelDraft as? DateTimeDraft ?: DateTimeDraft()
            DateTimeDraftEditorContent(
                draft = draft,
                onDraftChange = { next ->
                    state.updatePanelDraft(next, edited = dateTimeDraftContentChanged(draft, next))
                },
                environment = environment,
                format = format,
                codec = codec,
                navigationState = navigationState,
                constraints = constraints,
                enabled = enabled,
                readOnly = readOnly,
                style = activeStyle,
                slots = slots,
                onSubmit = { state.applyPanel(environment.sampleNow()) },
                rawInput = rawInput,
                onRawInputChange = { next ->
                    if (next.composition != null) {
                        state.updatePanelRawInput(next)
                    } else if (next.text.isEmpty()) {
                        state.clearPanel()
                    } else {
                        when (val result = parsePickerText(codec, next.text, environment.locale)) {
                            is ParseResult.Parsed -> state.replacePanelRawInput(
                                DateTimeDraft.from(result.value, format, environment.locale),
                            )

                            else -> state.updatePanelRawInput(next)
                        }
                    }
                },
                rawIssue = rawInput?.let {
                    parseIssue(parsePickerText(codec, it.text, environment.locale))
                },
                strings = strings,
            )
        },
        actions = {
            DateTimePickerActions(
                state = state,
                environment = environment,
                format = format,
                constraints = constraints,
                slots = slots,
            )
        },
    )
}

/**
 * 记住 [DatePickerField] 使用的状态。
 */
@Composable
fun rememberDatePickerFieldState(): PickerFieldState<LocalDate> =
    remember { PickerFieldState() }
    // remember { PickerFieldState(picker.foundation.PickerFieldKind.Date) }

/**
 * 记住 [TimePickerField] 使用的状态。
 */
@Composable
fun rememberTimePickerFieldState(): PickerFieldState<LocalTime> =
    remember { PickerFieldState() }
    // remember { PickerFieldState(picker.foundation.PickerFieldKind.Time) }

/**
 * 记住 [DateTimePickerField] 使用的状态。
 */
@Composable
fun rememberDateTimePickerFieldState(): PickerFieldState<LocalDateTime> =
    remember { PickerFieldState() }
    // remember { PickerFieldState(picker.foundation.PickerFieldKind.DateTime) }

/**
 * 日期、时间与日期时间适配器共用的字段外壳。
 */
@Composable
internal fun <T : Any> PickerFieldFrame(
    /**
     * 持有当前弹层会话与提交协议的字段状态。
     */
    state: PickerFieldState<T>,
    /**
     * 用于为事件时校验采样当前时间的环境。
     */
    environment: PickerEnvironment,
    /**
     * 应用于字段列的修饰符。
     */
    modifier: Modifier,
    /**
     * 直接输入与弹层控件是否接受输入。
     */
    enabled: Boolean,
    /**
     * 控件是否保持可见但不接受编辑。
     */
    readOnly: Boolean,
    /**
     * 已解析的字段与弹层样式。
     */
    style: PickerStyle,
    /**
     * 用于显示状态问题的本地化消息。
     */
    strings: PickerStrings,
    /**
     * 可选标签内容。
     */
    label: (@Composable () -> Unit)?,
    /**
     * 状态消息之后渲染的可选内容。
     */
    supportingText: (@Composable () -> Unit)?,
    /**
     * 字段为空时显示的编解码提示。
     */
    fieldHint: String,
    /**
     * 打开具体选择器面板。
     */
    onOpen: () -> Unit,
    /**
     * 渲染具体面板主体。
     */
    panel: @Composable () -> Unit,
    /**
     * 渲染具体面板操作区。
     */
    actions: @Composable () -> Unit,
    /**
     * 处理 Escape，包括子视图返回导航。
     */
    onEscape: () -> Unit = { state.closePanelWithoutApplying() },
    /**
     * 按下键盘快捷键时应用面板。
     */
    onApply: () -> Unit = {},
    /**
     * 对隐藏时间精度的可选说明。
     */
    hiddenPrecision: String? = null,
) {
    val borderColor = when {
        state.issue != null -> style.colors.error
        !enabled -> style.colors.disabled
        else -> style.colors.outline
    }
    Column(modifier = modifier) {
        label?.invoke()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = style.dimensions.fieldMinHeight)
                .background(style.colors.surface, style.shapes.field)
                .border(style.dimensions.borderWidth, borderColor, style.shapes.field)
                .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
                .semantics { testTag = PickerSemantics.FIELD },

            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicTextField(
                    value = state.inputValue,
                    onValueChange = state::updateInput,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focus ->
                            // 点击清空或下拉按钮会让文本框失焦；指针标记可阻止在点击前提交临时值。
                            if (!focus.isFocused && state.consumePointerAction()) return@onFocusChanged
                            if (
                                !focus.isFocused &&
                                enabled &&
                                !readOnly &&
                                !state.isPopupOpen &&
                                !state.hasComposition
                            ) {
                                state.submitDirect(environment.sampleNow())
                            }
                        }
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.Enter &&
                                enabled &&
                                !readOnly &&
                                !state.hasComposition
                            ) {
                                state.submitDirect(environment.sampleNow())
                                true
                            } else {
                                false
                            }
                        },
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = true,
                    textStyle = style.typography.body,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    decorationBox = { inner ->
                        Box {
                            if (state.inputValue.text.isEmpty()) {
                                BasicText(fieldHint, style = style.typography.caption)
                            }
                            inner()
                        }
                    },
                )
                if (state.inputValue.text.isNotEmpty()) {
                    PickerIcon(
                        kind = PickerIconKind.Clear,
                        contentDescription = "清空",
                        enabled = enabled && !readOnly,
                        onPress = if (enabled && !readOnly) state::markPointerAction else null,
                        onClick = {
                            state.clearPointerAction()
                            state.clearDirect()
                            // state.clearDirect(environment.sampleNow())
                        },
                        style = style,
                    )
                }
                PickerIcon(
                    kind = PickerIconKind.ChevronDown,
                    contentDescription = if (state.isPopupOpen) "关闭选择面板" else "打开选择面板",
                    stateDescription = if (state.isPopupOpen) "已展开" else "已收起",
                    enabled = enabled && !readOnly,
                    onPress = if (enabled && !readOnly && !state.isPopupOpen) {
                        state::markPointerAction
                    } else {
                        null
                    },
                    onClick = {
                        state.clearPointerAction()
                        if (state.isPopupOpen) {
                            state.closePanelWithoutApplying()
                        } else {
                            onOpen()
                        }
                    },
                    style = style,
                )
            }
        }

        val issue = state.issue
        if (issue != null) {
            BasicText(
                text = strings.message(issue),
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                style = style.typography.caption.copy(color = style.colors.error),
            )
        }
        hiddenPrecision?.let { hint ->
            BasicText(
                text = hint,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                style = style.typography.caption,
            )
        }
        if (
            issue?.code in setOf(
                PickerIssueCodes.ExternalConflict,
                PickerIssueCodes.ZoneConflict,
                PickerIssueCodes.PrecisionConflict,
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            ) {
                BasicText(
                    text = strings.loadExternal,
                    modifier = Modifier
                        .clickable { state.loadExternalValue() }
                        .semantics { contentDescription = strings.loadExternal },
                    style = style.typography.caption.copy(color = style.colors.focus),
                )
                BasicText(
                    text = strings.keepEditing,
                    modifier = Modifier
                        .clickable { state.keepEditing() }
                        .semantics { contentDescription = strings.keepEditing },
                    style = style.typography.caption.copy(color = style.colors.focus),
                )
            }
        }
        supportingText?.invoke()
    }

    if (state.isPopupOpen) {
        val positionProvider = rememberPickerPopupPositionProvider(
            popupGap = style.dimensions.popupGap,
            windowMargin = style.dimensions.windowMargin,
        )
        PickerPopup(
            onDismissRequest = state::closePanelWithoutApplying,
            positionProvider = positionProvider,
            onEscape = onEscape,
            shouldHandleEscape = { !state.hasComposition },
        ) {
            PickerTheme(style = style) {
                Column(
                    modifier = Modifier
                        .widthIn(min = style.dimensions.panelPreferredWidth)
                        .shadow(style.dimensions.panelElevation, style.shapes.panel)
                        .background(style.colors.surface, style.shapes.panel)
                        .border(style.dimensions.borderWidth, style.colors.outline, style.shapes.panel)
                        .padding(style.dimensions.panelPadding)
                        .semantics { testTag = PickerSemantics.POPUP }
                        .onPreviewKeyEvent { event ->
                            if (
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.Enter &&
                                (event.isCtrlPressed || event.isMetaPressed) &&
                                enabled &&
                                !readOnly &&
                                !state.hasComposition
                            ) {
                                onApply()
                                true
                            } else {
                                false
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(style.dimensions.sectionGap),
                ) {
                    panel()
                    actions()
                }
            }
        }
    }
}

@Composable
private fun DatePickerDraftEditorContent(
    state: PickerFieldState<LocalDate>,
    environment: PickerEnvironment,
    navigationState: CalendarNavigationState,
    constraints: DateConstraints,
    style: PickerStyle,
    slots: DatePickerSlots,
    enabled: Boolean,
    readOnly: Boolean,
    codec: picker.core.PickerTextCodec<LocalDate>,
    onSubmit: () -> Unit,
    strings: PickerStrings,
) {
    val rawInput = state.panelRawInput
    val draft = state.panelDraft as? DateDraft ?: DateDraft()
    DatePickerDraftEditorContent(
        draft = draft,
        onDraftChange = { next ->
            if (rawInput == null) {
                state.updatePanelDraft(next, edited = dateDraftContentChanged(draft, next))
            } else {
                when (resolveDraft(next, codec, environment.locale)) {
                    is ParseResult.Parsed -> state.replacePanelRawInput(next)
                    else -> state.updatePanelRawInput(next.input)
                }
            }
        },
        environment = environment,
        navigationState = navigationState,
        constraints = constraints,
        enabled = enabled,
        readOnly = readOnly,
        style = style,
        slots = slots,
        codec = codec,
        onSubmit = onSubmit,
        rawInput = rawInput,
        onRawInputChange = { next ->
            if (next.composition != null) {
                state.updatePanelRawInput(next)
            } else if (next.text.isEmpty()) {
                state.clearPanel()
            } else {
                when (parsePickerText(codec, next.text, environment.locale)) {
                    is ParseResult.Parsed -> state.replacePanelRawInput(DateDraft(next))
                    else -> state.updatePanelRawInput(next)
                }
            }
        },
        rawIssue = rawInput?.let {
            parseIssue(parsePickerText(codec, it.text, environment.locale))
        },
        strings = strings,
    )
}

@Composable
private fun DatePickerActions(
    state: PickerFieldState<LocalDate>,
    environment: PickerEnvironment,
    constraints: DateConstraints,
    codec: picker.core.PickerTextCodec<LocalDate>,
    slots: DatePickerSlots,
) {
    val now = environment.sampleNow()
    slots.actions(
        PickerActionsScope(
            canApply = state.canApply(now),
            canClear = true,
            canUseNow = picker.core.validateDate(now.date, constraints, now) is ValidationResult.Valid,
            canUseToday = picker.core.validateDate(now.date, constraints, now) is ValidationResult.Valid,
            apply = { state.applyPanel(environment.sampleNow()) },
            cancel = state::closePanelWithoutApplying,
            clear = state::clearPanel,
            now = {
                val current = environment.sampleNow()
                if (picker.core.validateDate(current.date, constraints, current) is ValidationResult.Valid) {
                    state.updatePanelDraft(
                        DateDraft(picker.foundation.textFieldValue(codec.format(current.date, environment.locale)))
                    )
                }
            },
            today = {
                val current = environment.sampleNow()
                if (picker.core.validateDate(current.date, constraints, current) is ValidationResult.Valid) {
                    state.updatePanelDraft(
                        DateDraft(picker.foundation.textFieldValue(codec.format(current.date, environment.locale)))
                    )
                }
            },
        )
    )
}

@Composable
private fun TimePickerActions(
    state: PickerFieldState<LocalTime>,
    environment: PickerEnvironment,
    format: TimeFormatOptions,
    constraints: picker.core.TimeConstraints,
    slots: TimePickerSlots,
) {
    val now = environment.sampleNow()
    val candidateNow = truncateForPrecision(now.time, format)
    slots.actions(
        PickerActionsScope(
            canApply = state.canApply(now),
            canClear = true,
            canUseNow = picker.core.validateTime(candidateNow, constraints, now) is ValidationResult.Valid,
            canUseToday = false,
            apply = { state.applyPanel(environment.sampleNow()) },
            cancel = state::closePanelWithoutApplying,
            clear = state::clearPanel,
            now = {
                val current = environment.sampleNow()
                val candidate = truncateForPrecision(current.time, format)
                if (picker.core.validateTime(candidate, constraints, current) is ValidationResult.Valid) {
                    state.updatePanelDraft(TimeDraft.from(candidate, format).copy(forcePrecisionReset = true))
                }
            },
            today = {
                val current = environment.sampleNow()
                if (picker.core.validateTime(current.time, constraints, current) is ValidationResult.Valid) {
                    state.updatePanelDraft(TimeDraft.from(current.time, format))
                }
            },
        )
    )
}

@Composable
private fun DateTimePickerActions(
    state: PickerFieldState<LocalDateTime>,
    environment: PickerEnvironment,
    format: TimeFormatOptions,
    constraints: DateTimeConstraints,
    slots: DateTimePickerSlots,
) {
    val now = environment.sampleNow()
    val candidateNow = LocalDateTime.of(now.date, truncateForPrecision(now.time, format))
    slots.actions(
        PickerActionsScope(
            canApply = state.canApply(now),
            canClear = true,
            canUseNow = picker.core.validateDateTime(candidateNow, constraints, now) is ValidationResult.Valid,
            canUseToday = picker.core.validateDate(now.date, constraints.dates, now) is ValidationResult.Valid,
            apply = { state.applyPanel(environment.sampleNow()) },
            cancel = state::closePanelWithoutApplying,
            clear = state::clearPanel,
            now = {
                val current = environment.sampleNow()
                val candidate = LocalDateTime.of(current.date, truncateForPrecision(current.time, format))
                if (picker.core.validateDateTime(candidate, constraints, current) is ValidationResult.Valid) {
                    val draft = DateTimeDraft.from(candidate, format, environment.locale)
                    state.updatePanelDraft(
                        draft.copy(time = draft.time.copy(forcePrecisionReset = true)),
                    )
                }
            },
            today = {
                val current = environment.sampleNow()
                if (picker.core.validateDate(current.date, constraints.dates, current) is ValidationResult.Valid) {
                    val draft = state.panelDraft as? DateTimeDraft ?: DateTimeDraft()
                    state.updatePanelDraft(
                        draft.copy(
                            date = DateDraft(
                                picker.foundation.textFieldValue(
                                    PickerCodecs.date().format(current.date, environment.locale)
                                )
                            )
                        )
                    )
                }
            },
        )
    )
}

private fun dateDraftContentChanged(before: DateDraft, after: DateDraft): Boolean =
    before.input.text != after.input.text || before.input.composition != after.input.composition

private fun timeDraftContentChanged(before: TimeDraft, after: TimeDraft): Boolean =
    before.hour.contentChanged(after.hour) ||
            before.minute.contentChanged(after.minute) ||
            before.second.contentChanged(after.second) ||
            before.period != after.period ||
            before.forcePrecisionReset != after.forcePrecisionReset

/**
 * 只比较可编辑内容，光标移动不影响脏状态判断。
 */
internal fun dateTimeDraftContentChanged(before: DateTimeDraft, after: DateTimeDraft): Boolean =
    dateDraftContentChanged(before.date, after.date) ||
            timeDraftContentChanged(before.time, after.time)

private fun TextFieldValue.contentChanged(other: TextFieldValue): Boolean =
    text != other.text || composition != other.composition

private fun dateConfiguration(
    value: LocalDate?,
    environment: PickerEnvironment,
    constraints: DateConstraints,
    required: Boolean,
    codec: picker.core.PickerTextCodec<LocalDate>,
): FieldConfiguration<LocalDate> = FieldConfiguration(
    kind = picker.foundation.PickerFieldKind.Date,
    contextKey = ContextKey(
        zoneId = environment.zoneId,
        locale = environment.locale,
        codec = codec,
    ),
    value = value,
    required = required,
    formatValue = { codec.format(it, environment.locale) },
    parseText = { parsePickerText(codec, it, environment.locale) },
    createDraft = { DateDraft.from(it, codec, environment.locale) },
    evaluateText = { input, original, now, _ ->
        evaluateParsed(
            result = parsePickerText(codec, input.text, environment.locale),
            original = original,
            now = now,
            required = required,
            normalize = { parsed, _ -> parsed },
            validate = { parsed, snapshot -> picker.core.validateDate(parsed, constraints, snapshot) },
        )
    },
    evaluateDraft = { draft, original, now, _ ->
        val parsed = (draft as? DateDraft)?.let {
            parsePickerText(codec, it.input.text, environment.locale)
        }
            ?: ParseResult.Empty
        evaluateParsed(
            result = parsed,
            original = original,
            now = now,
            required = required,
            normalize = { parsedValue, _ -> parsedValue },
            validate = { parsedValue, snapshot -> picker.core.validateDate(parsedValue, constraints, snapshot) },
        )
    },
    validateExternal = { external, now ->
        evaluationFromValidation(picker.core.validateDate(external, constraints, now), external)
    },
    // draftDate = { draft ->
    //     val parsed = (draft as? DateDraft)?.let { resolveDraft(it, codec, environment.locale) }
    //     (parsed as? ParseResult.Parsed)?.value
    // },
    hasComposition = { draft ->
        (draft as? DateDraft)?.input?.composition != null
    },
)

private fun timeConfiguration(
    value: LocalTime?,
    environment: PickerEnvironment,
    format: TimeFormatOptions,
    constraints: picker.core.TimeConstraints,
    required: Boolean,
    codec: picker.core.PickerTextCodec<LocalTime>,
): FieldConfiguration<LocalTime> = FieldConfiguration(
    kind = picker.foundation.PickerFieldKind.Time,
    contextKey = ContextKey(
        zoneId = environment.zoneId,
        locale = environment.locale,
        codec = codec,
        format = format,
    ),
    value = value,
    required = required,
    formatValue = { codec.format(it, environment.locale) },
    parseText = { parsePickerText(codec, it, environment.locale) },
    createDraft = { TimeDraft.from(it, format) },
    evaluateText = { input, original, now, _ ->
        evaluateParsed(
            result = parsePickerText(codec, input.text, environment.locale),
            original = original,
            now = now,
            required = required,
            normalize = { parsed, before ->
                parsed.mergeTimePrecision(
                    original = before,
                    format = format,
                )
            },
            validate = { parsed, snapshot -> picker.core.validateTime(parsed, constraints, snapshot) },
        )
    },
    evaluateDraft = { draft, original, now, _ ->
        val timeDraft = draft as? TimeDraft
        evaluateParsed(
            result = timeDraft?.let { resolveDraft(it, format, environment.locale) }
                ?: ParseResult.Empty,
            original = original,
            now = now,
            required = required,
            normalize = { parsed, before ->
                parsed.mergeTimePrecision(
                    original = before,
                    format = format,
                    forcePrecisionReset = timeDraft?.forcePrecisionReset == true,
                )
            },
            validate = { parsed, snapshot -> picker.core.validateTime(parsed, constraints, snapshot) },
        )
    },
    validateExternal = { external, now ->
        evaluationFromValidation(picker.core.validateTime(external, constraints, now), external)
    },
    // draftDate = { null },
    hasComposition = { draft ->
        val timeDraft = draft as? TimeDraft ?: return@FieldConfiguration false
        timeDraft.hasComposition
    },
)

private fun dateTimeConfiguration(
    value: LocalDateTime?,
    environment: PickerEnvironment,
    format: TimeFormatOptions,
    constraints: DateTimeConstraints,
    required: Boolean,
    codec: picker.core.PickerTextCodec<LocalDateTime>,
): FieldConfiguration<LocalDateTime> = FieldConfiguration(
    kind = picker.foundation.PickerFieldKind.DateTime,
    contextKey = ContextKey(
        zoneId = environment.zoneId,
        locale = environment.locale,
        codec = codec,
        format = format,
    ),
    value = value,
    required = required,
    formatValue = { codec.format(it, environment.locale) },
    parseText = { parsePickerText(codec, it, environment.locale) },
    createDraft = { DateTimeDraft.from(it, format, environment.locale) },
    evaluateText = { input, original, now, _ ->
        evaluateParsed(
            result = parsePickerText(codec, input.text, environment.locale),
            original = original,
            now = now,
            required = required,
            normalize = { parsed, before -> parsed.mergeDateTimePrecision(before, format) },
            validate = { parsed, snapshot -> picker.core.validateDateTime(parsed, constraints, snapshot) },
        )
    },
    evaluateDraft = { draft, original, now, _ ->
        val dateTimeDraft = draft as? DateTimeDraft
        evaluateParsed(
            result = dateTimeDraft?.let { resolveDraft(it, format, environment.locale) }
                ?: ParseResult.Empty,
            original = original,
            now = now,
            required = required,
            normalize = { parsed, before ->
                parsed.mergeDateTimePrecision(
                    original = before,
                    format = format,
                    forcePrecisionReset = dateTimeDraft?.time?.forcePrecisionReset == true,
                )
            },
            validate = { parsed, snapshot -> picker.core.validateDateTime(parsed, constraints, snapshot) },
        )
    },
    validateExternal = { external, now ->
        evaluationFromValidation(picker.core.validateDateTime(external, constraints, now), external)
    },
    // draftDate = { draft ->
    //     val parsed = (draft as? DateTimeDraft)?.let { resolveDraft(it, format, environment.locale) }
    //     (parsed as? ParseResult.Parsed)?.value?.toLocalDate()
    // },
    hasComposition = { draft ->
        val dateTimeDraft = draft as? DateTimeDraft ?: return@FieldConfiguration false
        dateTimeDraft.hasComposition
    },
)

private data class ContextKey(
    val zoneId: java.time.ZoneId,
    val locale: java.util.Locale,
    val codec: Any,
    val format: Any? = null,
)

private fun <T : Any> evaluateParsed(
    result: ParseResult<T>,
    original: T?,
    now: PickerNow,
    required: Boolean,
    normalize: (T, T?) -> T,
    validate: (T, PickerNow) -> ValidationResult,
): SubmitEvaluation<T> {
    // 解析与校验保持分离：未完成值应可继续修正，完整但不允许的值应能说明原因。
    return when (result) {
        ParseResult.Empty -> if (required) {
            SubmitEvaluation.Blocked(picker.core.PickerIssue(PickerIssueCodes.Required))
        } else {
            SubmitEvaluation.Ready(null)
        }

        ParseResult.Incomplete -> SubmitEvaluation.Blocked(
            picker.core.PickerIssue(PickerIssueCodes.Incomplete),
        )

        is ParseResult.Invalid -> SubmitEvaluation.Blocked(result.issue)
        is ParseResult.Parsed -> {
            val candidate = normalize(result.value, original)
            evaluationFromValidation(validate(candidate, now), candidate)
        }
    }
}

private fun <T : Any> evaluationFromValidation(
    result: ValidationResult,
    value: T,
): SubmitEvaluation<T> =
    when (result) {
        ValidationResult.Valid -> SubmitEvaluation.Ready(value)
        is ValidationResult.Invalid -> SubmitEvaluation.Blocked(result.issue)
    }

private fun LocalTime.mergeTimePrecision(
    original: LocalTime?,
    format: TimeFormatOptions,
    forcePrecisionReset: Boolean = false,
): LocalTime {
    val changed = forcePrecisionReset || original == null || visibleTimeChanged(original, this, format)
    return picker.core.mergeEditedTime(original, this, format, changed)
}

private fun LocalDateTime.mergeDateTimePrecision(
    original: LocalDateTime?,
    format: TimeFormatOptions,
    forcePrecisionReset: Boolean = false,
): LocalDateTime {
    val originalTime = original?.toLocalTime()
    val changed = forcePrecisionReset || originalTime == null || visibleTimeChanged(originalTime, toLocalTime(), format)
    val mergedTime = picker.core.mergeEditedTime(originalTime, toLocalTime(), format, changed)
    return LocalDateTime.of(toLocalDate(), mergedTime)
}
