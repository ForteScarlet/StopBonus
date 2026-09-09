package picker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import picker.core.DateConstraints
import picker.core.InstantConstraints
import picker.core.InstantResolution
import picker.core.ParseResult
import picker.core.PickerCodecs
import picker.core.PickerEnvironment
import picker.core.PickerIssue
import picker.core.PickerIssueCodes
import picker.core.PickerNow
import picker.core.PickerStrings
import picker.core.TimeFormatOptions
import picker.core.ValidationResult
import picker.core.hiddenPrecisionDescription
import picker.core.mergeEditedTime
import picker.core.parsePickerText
import picker.core.resolveInstant
import picker.core.sampleNow
import picker.core.truncateForPrecision
import picker.core.validateDate
import picker.core.validateInstant
import picker.core.visibleTimeChanged
import picker.foundation.CalendarNavigationState
import picker.foundation.CalendarView
import picker.foundation.DateDraft
import picker.foundation.DateTimeDraft
import picker.foundation.FieldConfiguration
import picker.foundation.PickerFieldKind
import picker.foundation.PickerFieldState
import picker.foundation.SubmitEvaluation
import picker.foundation.PickerSemantics
import picker.foundation.hasComposition
import picker.foundation.resolveDraft
import picker.foundation.rememberCalendarNavigationState
import picker.foundation.textFieldValue
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 即时时刻选择器的面板草稿，集中保存本地日期时间、可选重叠偏移及原始时刻。
 */
internal data class InstantPanelDraft(
    /**
     * 可编辑的本地日期时间部分。
     */
    val dateTime: DateTimeDraft,
    /**
     * 夏令时重叠时显式选择的偏移；尚未解决时为空。
     */
    val selectedOffset: ZoneOffset?,
    /**
     * 合并时用于保留精度的原始时刻。
     */
    val originalInstant: Instant?,
)

/**
 * 渲染显式处理时区与夏令时的绝对时刻字段。
 *
 * 可见编辑器使用本地日期时间文本，提交时依据 [environment.zoneId] 解析，
 * 不会擅自跨越夏令时缺口，也不会替用户选择重叠偏移。
 */
@Composable
fun InstantPickerField(
    /**
     * 外部已提交的绝对时刻；字段为空时为 null。
     */
    value: Instant?,
    /**
     * 所有者接受候选值后接收该值。
     */
    onValueChange: (Instant?) -> Unit,
    /**
     * 时钟、显示时区、语言环境及日历周配置。
     */
    environment: PickerEnvironment,
    /**
     * 应用于字段外壳的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 跨重组与弹层会话保留的状态。
     */
    state: PickerFieldState<Instant> = rememberInstantPickerFieldState(),
    /**
     * 本地编辑器使用的小时制与可见精度。
     */
    format: TimeFormatOptions = TimeFormatOptions(),
    /**
     * 将时刻投影到目标时区后应用的本地日期约束。
     */
    dateConstraints: DateConstraints = DateConstraints(),
    /**
     * 绝对时刻约束及附加校验。
     */
    constraints: InstantConstraints = InstantConstraints(),
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
     * 直接输入与面板整体输入共用的本地日期时间编解码器。
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
        config = instantConfiguration(
            value = value,
            environment = environment,
            format = format,
            dateConstraints = dateConstraints,
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
        supportingText = {
            BasicText(
                text = environment.zoneId.id + " · " + offsetText(value?.atZone(environment.zoneId)?.offset),
                style = activeStyle.typography.caption,
            )
            supportingText?.invoke()
        },
        fieldHint = codec.formatHint,
        hiddenPrecision = if (value != null && hiddenPrecisionDescription(
                value.atZone(environment.zoneId).toLocalTime(), format
            )
        ) {
            strings.hiddenPrecision
        } else {
            null
        },
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
            val draft = state.panelDraft as? InstantPanelDraft
                ?: InstantPanelDraft(DateTimeDraft(), null, value)
            InstantPanelContent(
                state = state,
                draft = draft,
                environment = environment,
                format = format,
                codec = codec,
                dateConstraints = dateConstraints,
                constraints = constraints,
                navigationState = navigationState,
                style = activeStyle,
                slots = slots,
                enabled = enabled,
                readOnly = readOnly,
                strings = strings,
            )
        },
        actions = {
            InstantPickerActions(
                state = state,
                environment = environment,
                format = format,
                dateConstraints = dateConstraints,
                constraints = constraints,
                slots = slots,
            )
        },
    )
}

/**
 * 记住 [InstantPickerField] 使用的状态。
 */
@Composable
fun rememberInstantPickerFieldState(): PickerFieldState<Instant> =
    remember { PickerFieldState() }

/**
 * 不携带 Popup、抽屉、对话框或底部操作栏的即时选择面板。
 *
 * 它只负责编辑本地日期时间并在候选值合法时回调 [onValueChange]。容器、确认、
 * 清空和“现在”等业务动作均由调用方组合，因此同一内容可置于弹出框、对话框或独立
 * 窗口中。
 */
@Composable
fun InstantPickerPanel(
    /**
     * 当前真实时刻；用于初始化和外部同步。
     */
    value: Instant?,
    /**
     * 接收已通过日期、夏令时与绝对时刻约束校验的候选值。
     */
    onValueChange: (Instant) -> Unit,
    /**
     * 时钟、显示时区、语言环境及周起始日。
     */
    environment: PickerEnvironment,
    /**
     * 应用于内容根节点的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 跨容器重组保留的日历导航状态。
     */
    navigationState: CalendarNavigationState = rememberCalendarNavigationState(),
    /**
     * 时间的小时制与可见精度。
     */
    format: TimeFormatOptions = TimeFormatOptions(),
    /**
     * 日历可选日期范围。
     */
    dateConstraints: DateConstraints = DateConstraints(),
    /**
     * 真实时刻的最终限制。
     */
    constraints: InstantConstraints = InstantConstraints(),
    /**
     * 是否接受编辑。
     */
    enabled: Boolean = true,
    /**
     * 是否仅展示当前值。
     */
    readOnly: Boolean = false,
    /**
     * 视觉样式；默认从最近的 [PickerTheme] 取得。
     */
    style: PickerStyle = PickerTheme.style,
    /**
     * 可替换的日历与时间编辑器槽位。
     */
    slots: DateTimePickerSlots = DateTimePickerDefaults.slots(),
) {
    val activeStyle = effectiveStyle(style)
    val localValue = value?.atZone(environment.zoneId)?.toLocalDateTime()
    var draft by remember(value, environment.zoneId, environment.locale, format) {
        mutableStateOf(DateTimeDraft.from(localValue, format, environment.locale))
    }
    val now = environment.sampleNow()

    DateTimeDraftEditorContent(
        draft = draft,
        onDraftChange = { next ->
            draft = next
            val local = (resolveDraft(next, format, environment.locale) as? ParseResult.Parsed)?.value
                ?: return@DateTimeDraftEditorContent
            val resolution = resolveInstant(local, environment.zoneId)
            val candidate = (resolution as? InstantResolution.Unique)?.candidate?.instant
                ?: return@DateTimeDraftEditorContent
            if (
                validateDate(local.toLocalDate(), dateConstraints, now) is ValidationResult.Valid &&
                validateInstant(candidate, constraints, now) is ValidationResult.Valid
            ) {
                onValueChange(candidate)
            }
        },
        environment = environment,
        format = format,
        navigationState = navigationState,
        constraints = picker.core.DateTimeConstraints(dates = dateConstraints),
        enabled = enabled,
        readOnly = readOnly,
        style = activeStyle,
        slots = slots,
    )
}

@Composable
private fun InstantPanelContent(
    state: PickerFieldState<Instant>,
    draft: InstantPanelDraft,
    environment: PickerEnvironment,
    format: TimeFormatOptions,
    codec: picker.core.PickerTextCodec<LocalDateTime>,
    dateConstraints: DateConstraints,
    constraints: InstantConstraints,
    navigationState: CalendarNavigationState,
    style: PickerStyle,
    slots: DateTimePickerSlots,
    enabled: Boolean,
    readOnly: Boolean,
    strings: PickerStrings,
) {
    // 单次面板渲染共用一个时间快照，避免两个重叠偏移选项在时钟边界产生不一致的可用状态。
    val panelNow = environment.sampleNow()
    val rawInput = state.panelRawInput
    val editorSlots = DateTimePickerSlots(
        dayContent = slots.dayContent,
        header = slots.header,
        timeContent = slots.timeContent,
        actions = slots.actions,
    )

    DateTimeDraftEditorContent(
        draft = draft.dateTime,
        onDraftChange = { dateTime ->
            val contentChanged = dateTimeDraftContentChanged(draft.dateTime, dateTime)
            state.updatePanelDraft(
                draft.copy(
                    dateTime = dateTime,
                    selectedOffset = if (contentChanged) null else draft.selectedOffset,
                ),
                edited = contentChanged,
            )
        },
        environment = environment,
        format = format,
        codec = codec,
        navigationState = navigationState,
        constraints = picker.core.DateTimeConstraints(dates = dateConstraints),
        enabled = enabled,
        readOnly = readOnly,
        style = style,
        slots = editorSlots,
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
                        InstantPanelDraft(
                            dateTime = DateTimeDraft.from(result.value, format, environment.locale),
                            selectedOffset = null,
                            originalInstant = draft.originalInstant,
                        ),
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

    val localResult = resolveDraft(draft.dateTime, format, environment.locale)
    val local = (localResult as? ParseResult.Parsed)?.value
    if (local != null) {
        when (val resolution = resolveInstant(local, environment.zoneId)) {
            is InstantResolution.Unique -> {
                BasicText(
                    text = offsetText(resolution.candidate.offset),
                    style = style.typography.caption,
                )
            }

            is InstantResolution.Gap -> {
                BasicText(
                    text = strings.dstGap,
                    modifier = Modifier.padding(top = 4.dp),
                    style = style.typography.caption.copy(color = style.colors.error),
                )
            }

            is InstantResolution.Overlap -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(style.colors.mutedSurface, style.shapes.control)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BasicText(strings.offsetRequired, style = style.typography.caption)
                    resolution.candidates.forEachIndexed { index, candidate ->
                        val candidateResult = validateInstant(candidate.instant, constraints, panelNow)
                        val isEnabled = candidateResult is ValidationResult.Valid
                        val label = (if (index == 0) "第一次" else "第二次") +
                                " · " + offsetText(candidate.offset)
                        BasicText(
                            text = label,
                            modifier = Modifier
                                .clickable(enabled = isEnabled) {
                                    state.updatePanelDraft(draft.copy(selectedOffset = candidate.offset))
                                }
                                .semantics {
                                    contentDescription = label
                                    selected = draft.selectedOffset == candidate.offset
                                    role = Role.Button
                                    testTag = PickerSemantics.offset(index)
                                    if (!isEnabled) disabled()
                                }
                                .padding(4.dp),
                            style = style.typography.body.copy(
                                color = if (isEnabled) style.colors.onSurface else style.colors.disabled,
                            ),
                        )
                        if (!isEnabled && candidateResult is ValidationResult.Invalid) {
                            BasicText(
                                text = PickerStrings.forLocale(environment.locale).message(candidateResult.issue),
                                style = style.typography.caption.copy(color = style.colors.error),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstantPickerActions(
    state: PickerFieldState<Instant>,
    environment: PickerEnvironment,
    format: TimeFormatOptions,
    dateConstraints: DateConstraints,
    constraints: InstantConstraints,
    slots: DateTimePickerSlots,
) {
    val now = environment.sampleNow()
    val nowCandidate = instantNowCandidate(now, format, environment)
    val dateValid = nowCandidate?.let {
        validateDate(it.instant.atZone(environment.zoneId).toLocalDate(), dateConstraints, now)
    } is ValidationResult.Valid
    val instantValid = nowCandidate?.let { validateInstant(it.instant, constraints, now) } is ValidationResult.Valid
    slots.actions(
        PickerActionsScope(
            canApply = state.canApply(now),
            canClear = true,
            canUseNow = dateValid && instantValid,
            canUseToday = validateDate(now.date, dateConstraints, now) is ValidationResult.Valid,
            apply = { state.applyPanel(environment.sampleNow()) },
            cancel = state::closePanelWithoutApplying,
            clear = state::clearPanel,
            now = {
                val current = environment.sampleNow()
                val candidate = instantNowCandidate(current, format, environment)
                val currentDateValid = candidate?.let {
                    validateDate(it.instant.atZone(environment.zoneId).toLocalDate(), dateConstraints, current)
                } is ValidationResult.Valid
                val currentInstantValid =
                    candidate?.let { validateInstant(it.instant, constraints, current) } is ValidationResult.Valid
                if (candidate != null && currentDateValid && currentInstantValid) {
                    val dateTime = DateTimeDraft.from(
                        candidate.instant.atZone(environment.zoneId).toLocalDateTime(),
                        format,
                        environment.locale,
                    )
                    state.updatePanelDraft(
                        InstantPanelDraft(
                            dateTime = dateTime.copy(
                                time = dateTime.time.copy(forcePrecisionReset = true),
                            ),
                            selectedOffset = candidate.offset,
                            originalInstant = candidate.instant,
                        )
                    )
                }
            },
            today = {
                val currentNow = environment.sampleNow()
                if (validateDate(currentNow.date, dateConstraints, currentNow) is ValidationResult.Valid) {
                    val current = state.panelDraft as? InstantPanelDraft ?: InstantPanelDraft(
                        DateTimeDraft(),
                        null,
                        null,
                    )
                    state.updatePanelDraft(
                        current.copy(
                            dateTime = current.dateTime.copy(
                                date = DateDraft(
                                    textFieldValue(PickerCodecs.date().format(currentNow.date, environment.locale))
                                )
                            ),
                            selectedOffset = null,
                        )
                    )
                }
            },
        )
    )
}

private fun instantConfiguration(
    value: Instant?,
    environment: PickerEnvironment,
    format: TimeFormatOptions,
    dateConstraints: DateConstraints,
    constraints: InstantConstraints,
    required: Boolean,
    codec: picker.core.PickerTextCodec<LocalDateTime>,
): FieldConfiguration<Instant> = FieldConfiguration(
    kind = PickerFieldKind.Instant,
    contextKey = InstantContextKey(
        zoneId = environment.zoneId,
        locale = environment.locale,
        codec = codec,
        format = format,
    ),
    value = value,
    required = required,
    formatValue = { instant ->
        codec.format(instant.atZone(environment.zoneId).toLocalDateTime(), environment.locale)
    },
    parseText = { text -> parseInstantText(text, codec, environment) },
    createDraft = { instant ->
        val local = instant?.atZone(environment.zoneId)?.toLocalDateTime()
        InstantPanelDraft(
            dateTime = DateTimeDraft.from(local, format, environment.locale),
            selectedOffset = instant?.atZone(environment.zoneId)?.offset,
            originalInstant = instant,
        )
    },
    evaluateText = { input, original, now, _ ->
        evaluateInstantLocal(
            localResult = parsePickerText(codec, input.text, environment.locale),
            original = original,
            environment = environment,
            format = format,
            dateConstraints = dateConstraints,
            constraints = constraints,
            required = required,
            now = now,
            selectedOffset = null,
            requireExplicitOverlapChoice = false,
        )
    },
    evaluateDraft = { draft, original, now, changed ->
        val instantDraft = draft as? InstantPanelDraft
            ?: return@FieldConfiguration SubmitEvaluation.Blocked(
                PickerIssue(PickerIssueCodes.NotReady),
            )
        val localResult = resolveDraft(instantDraft.dateTime, format, environment.locale)
        evaluateInstantLocal(
            localResult = localResult,
            original = original,
            environment = environment,
            format = format,
            dateConstraints = dateConstraints,
            constraints = constraints,
            required = required,
            now = now,
            selectedOffset = instantDraft.selectedOffset,
            requireExplicitOverlapChoice = changed,
            forcePrecisionReset = instantDraft.dateTime.time.forcePrecisionReset,
        )
    },
    validateExternal = { external, now ->
        validateInstantValue(external, environment, dateConstraints, constraints, now)
    },
    // rebaseDraft = ,
    // draftDate = { draft ->
    //     val instantDraft = draft as? InstantPanelDraft ?: return@FieldConfiguration null
    //     val localResult = resolveDraft(instantDraft.dateTime, format, environment.locale)
    //     (localResult as? ParseResult.Parsed)?.value?.toLocalDate()
    // },
    hasComposition = { draft ->
        val instantDraft = draft as? InstantPanelDraft ?: return@FieldConfiguration false
        instantDraft.dateTime.hasComposition
    },
    rebaseDraft = { draft ->
        (draft as? InstantPanelDraft)?.copy(selectedOffset = null) ?: draft
    },
)

private data class InstantContextKey(
    val zoneId: java.time.ZoneId,
    val locale: java.util.Locale,
    val codec: Any,
    val format: TimeFormatOptions,
)

private fun parseInstantText(
    text: String,
    codec: picker.core.PickerTextCodec<LocalDateTime>,
    environment: PickerEnvironment,
): ParseResult<Instant> =
    when (val localResult = parsePickerText(codec, text, environment.locale)) {
        ParseResult.Empty -> ParseResult.Empty
        ParseResult.Incomplete -> ParseResult.Incomplete
        is ParseResult.Invalid -> localResult
        is ParseResult.Parsed -> when (val resolution = resolveInstant(localResult.value, environment.zoneId)) {
            is InstantResolution.Unique -> ParseResult.Parsed(resolution.candidate.instant)
            is InstantResolution.Gap -> ParseResult.Invalid(PickerIssue(PickerIssueCodes.DstGap))
            is InstantResolution.Overlap -> ParseResult.Invalid(PickerIssue(PickerIssueCodes.OffsetRequired))
        }
    }

private fun evaluateInstantLocal(
    localResult: ParseResult<LocalDateTime>,
    original: Instant?,
    environment: PickerEnvironment,
    format: TimeFormatOptions,
    dateConstraints: DateConstraints,
    constraints: InstantConstraints,
    required: Boolean,
    now: PickerNow,
    selectedOffset: ZoneOffset?,
    requireExplicitOverlapChoice: Boolean,
    forcePrecisionReset: Boolean = false,
): SubmitEvaluation<Instant> {
    return when (localResult) {
        ParseResult.Empty -> if (required) {
            SubmitEvaluation.Blocked(PickerIssue(PickerIssueCodes.Required))
        } else {
            SubmitEvaluation.Ready(null)
        }

        ParseResult.Incomplete -> SubmitEvaluation.Blocked(PickerIssue(PickerIssueCodes.Incomplete))
        is ParseResult.Invalid -> SubmitEvaluation.Blocked(localResult.issue)
        is ParseResult.Parsed -> {
            val local = localResult.value
            val dateValidation = validateDate(local.toLocalDate(), dateConstraints, now)
            if (dateValidation is ValidationResult.Invalid) {
                return SubmitEvaluation.Blocked(dateValidation.issue)
            }

            val originalLocal = original?.atZone(environment.zoneId)?.toLocalDateTime()
            val originalTime = originalLocal?.toLocalTime()
            val localTimeChanged = forcePrecisionReset || originalTime == null ||
                    visibleTimeChanged(originalTime, local.toLocalTime(), format)
            val effectiveLocal = if (originalTime == null) {
                local
            } else {
                LocalDateTime.of(
                    local.toLocalDate(),
                    mergeEditedTime(
                        original = originalTime,
                        edited = local.toLocalTime(),
                        options = format,
                        timeWasActuallyChanged = localTimeChanged,
                    ),
                )
            }
            if (
                original != null &&
                !requireExplicitOverlapChoice &&
                originalLocal == effectiveLocal &&
                !localTimeChanged
            ) {
                return validateInstantValue(original, environment, dateConstraints, constraints, now)
            }

            val resolution = resolveInstant(effectiveLocal, environment.zoneId)
            val candidate = when (resolution) {
                is InstantResolution.Unique -> resolution.candidate.instant
                is InstantResolution.Gap -> {
                    return SubmitEvaluation.Blocked(PickerIssue(PickerIssueCodes.DstGap))
                }

                is InstantResolution.Overlap -> {
                    val selected = resolution.candidates.firstOrNull { it.offset == selectedOffset }
                        ?: return SubmitEvaluation.Blocked(PickerIssue(PickerIssueCodes.OffsetRequired))
                    selected.instant
                }
            }
            validateInstantValue(candidate, environment, dateConstraints, constraints, now)
        }
    }
}

private fun validateInstantValue(
    instant: Instant,
    environment: PickerEnvironment,
    dateConstraints: DateConstraints,
    constraints: InstantConstraints,
    now: PickerNow,
): SubmitEvaluation<Instant> {
    val dateResult = validateDate(instant.atZone(environment.zoneId).toLocalDate(), dateConstraints, now)
    if (dateResult is ValidationResult.Invalid) return SubmitEvaluation.Blocked(dateResult.issue)
    return when (val result = validateInstant(instant, constraints, now)) {
        ValidationResult.Valid -> SubmitEvaluation.Ready(instant)
        is ValidationResult.Invalid -> SubmitEvaluation.Blocked(result.issue)
    }
}

private fun offsetText(offset: ZoneOffset?): String =
    offset?.let { "UTC" + it.id } ?: "尚未解析偏移"

private fun instantNowCandidate(
    now: PickerNow,
    format: TimeFormatOptions,
    environment: PickerEnvironment,
) = when (
    val resolution = resolveInstant(
        LocalDateTime.of(now.date, truncateForPrecision(now.time, format)),
        environment.zoneId,
    )
) {
    is InstantResolution.Unique -> resolution.candidate
    is InstantResolution.Gap -> null
    is InstantResolution.Overlap -> {
        val currentOffset = now.instant.atZone(environment.zoneId).offset
        resolution.candidates.firstOrNull { it.offset == currentOffset }
    }
}
