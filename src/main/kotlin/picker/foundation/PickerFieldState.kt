package picker.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import picker.core.ParseResult
import picker.core.PickerIssue
import picker.core.PickerIssueCodes
import picker.core.PickerNow

/**
 * 选择器字段的提交评估结果。
 */
sealed interface SubmitEvaluation<out T> {
    /**
     * 提交有效；值为空表示清除可选字段。
     */
    data class Ready<T>(
        /**
         * 候选值；清除可选字段时为空。
         */
        val value: T?,
    ) : SubmitEvaluation<T>

    /**
     * 提交被阻止，须修正输入或显式解决冲突。
     */
    data class Blocked(
        /**
         * 候选值不能提交的原因。
         */
        val issue: PickerIssue,
    ) : SubmitEvaluation<Nothing>
}

internal enum class PickerFieldKind {
    Date,
    Time,
    DateTime,
    Instant,
}

/**
 * 具体选择器字段提供给共享状态的类型擦除行为。
 */
internal class FieldConfiguration<T : Any>(
    /**
     * 用于选择上下文冲突语义的字段类型。
     */
    val kind: PickerFieldKind,
    /**
     * 当前绑定中解析器、格式及时区配置的稳定标识。
     */
    val contextKey: Any,
    /**
     * 最新的外部已提交值。
     */
    val value: T?,
    /**
     * 提交时是否拒绝空值。
     */
    val required: Boolean,
    /**
     * 将已提交值格式化为字段显示文本。
     */
    val formatValue: (T) -> String,
    /**
     * 解析完整或部分字段文本。
     */
    val parseText: (String) -> ParseResult<T>,
    /**
     * 根据值创建具体编辑器草稿。
     */
    val createDraft: (T?) -> Any?,
    /**
     * 结合当前外部值评估字段整体文本。
     */
    val evaluateText: (TextFieldValue, T?, PickerNow, Boolean) -> SubmitEvaluation<T>,
    /**
     * 结合当前外部值评估面板草稿。
     */
    val evaluateDraft: (Any?, T?, PickerNow, Boolean) -> SubmitEvaluation<T>,
    /**
     * 提交前校验未修改的外部值。
     */
    val validateExternal: (T, PickerNow) -> SubmitEvaluation<T>,
    /**
     * 判断具体草稿是否正在进行输入法组合。
     */
    val hasComposition: (Any?) -> Boolean = { false },
    /**
     * 用户选择保留编辑后重新建立草稿上下文。
     */
    val rebaseDraft: (Any?) -> Any? = { it },
)

/**
 * 等待外部状态持有者确认的值变更请求。
 */
private data class PendingRequest<T : Any>(
    /**
     * 发送给外部回调的候选值。
     */
    val candidate: T?,
    /**
     * 发出请求时的当前值。
     */
    val originalValue: T?,
    /**
     * 是否已经观察过一次未变化的外部绑定。
     */
    var checked: Boolean = false,
    /**
     * 接受候选值后是否关闭面板。
     */
    val closesPanel: Boolean,
)

/**
 * 区分显示文本、编辑草稿与外部已提交值的 Compose 字段状态。
 *
 * 状态不会假定 [currentOnValueChange] 立即接受候选值，而是等待下一次外部值绑定；
 * 回调拒绝或改变候选值时会暴露提交冲突。
 */
class PickerFieldState<T : Any> internal constructor() {
    private var owner: Any? = null
    private var contextKey: Any? = null
    private var observedValue: T? = null
    private var initialized = false
    private var resetRequested = false
    private var panelInputSnapshot: TextFieldValue = TextFieldValue()
    private var panelDirtySnapshot = false
    private var panelDirectEditedSnapshot = false
    private var directEdited = false
    private var panelEdited = false
    private var panelDraftValue: Any? by mutableStateOf(null)
    private var panelRawInputValue: TextFieldValue? by mutableStateOf(null)
    private var configuration: FieldConfiguration<T>? = null
    private var pendingRequest: PendingRequest<T>? = null
    private var currentOnValueChange: (T?) -> Unit = {}
    private var currentValue: T? = null
    private var pointerActionPending = false

    /**
     * 字段当前显示的文本，包含光标与输入法状态。
     */
    var inputValue: TextFieldValue by mutableStateOf(TextFieldValue())
        private set

    /**
     * 直接编辑或面板编辑是否改变了当前会话。
     */
    var isDirty: Boolean by mutableStateOf(false)
        private set

    /**
     * 此字段当前是否持有打开的选择器弹层会话。
     */
    var isPopupOpen: Boolean by mutableStateOf(false)
        private set

    /**
     * 字段当前显示的校验、冲突或就绪问题。
     */
    var issue: PickerIssue? by mutableStateOf(null)
        private set

    /**
     * 状态当前是否已绑定到一个可组合字段所有者。
     */
    var isBound: Boolean by mutableStateOf(false)
        private set

    /**
     * 当前 [issue] 是否阻止提交。
     */
    val hasBlockingIssue: Boolean
        get() = issue != null

    /**
     * 活动面板实现渲染的草稿对象。
     */
    internal val panelDraft: Any?
        get() = panelDraftValue

    /**
     * 尚不可解析时保留的面板整体输入。
     */
    internal val panelRawInput: TextFieldValue?
        get() = panelRawInputValue

    /**
     * 直接文本、原始文本或分段草稿是否正在进行输入法组合。
     */
    internal val hasComposition: Boolean
        get() = inputValue.composition != null ||
                panelRawInputValue?.composition != null ||
                configuration?.hasComposition?.invoke(panelDraftValue) == true

    internal fun bind(
        /**
         * 使用此状态的可组合字段标识。
         */
        owner: Any,
        /**
         * 当前值、格式、解析器与校验回调。
         */
        config: FieldConfiguration<T>,
        /**
         * 用于请求新外部提交值的回调。
         */
        onValueChange: (T?) -> Unit,
    ) {
        val existingOwner = this.owner
        if (existingOwner != null && existingOwner !== owner) {
            error("A PickerFieldState cannot be bound to two active fields")
        }
        this.owner = owner
        isBound = true
        currentOnValueChange = onValueChange
        configuration = config
        currentValue = config.value

        if (!initialized) {
            initialized = true
            observedValue = config.value
            contextKey = config.contextKey
            inputValue = formatValue(config.value)
        } else {
            synchronizeContext(config)
            synchronizeExternalValue(config.value)
        }

        if (resetRequested) {
            resetRequested = false
            resetFromExternal(config.value)
        }
    }

    internal fun unbind(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            isBound = false
            pointerActionPending = false
        }
    }

    /**
     * 标记会改变焦点的指针交互，避免将其误判为字段失焦提交。
     */
    internal fun markPointerAction() {
        pointerActionPending = true
    }

    /**
     * 指针动作分派后清除标记。
     */
    internal fun clearPointerAction() {
        pointerActionPending = false
    }

    /**
     * 消费并返回一次待处理的指针标记。
     */
    internal fun consumePointerAction(): Boolean {
        val pending = pointerActionPending
        pointerActionPending = false
        return pending
    }

    /**
     * 直接编辑结束后评估字段提交结果。
     */
    fun evaluateForSubmit(now: PickerNow): SubmitEvaluation<T> {
        val config = configuration ?: return blocked(PickerIssueCodes.NotReady)
        if (!isBound) return blocked(PickerIssueCodes.NotReady)
        if (resetRequested) return blocked(PickerIssueCodes.NotReady)
        if (isPopupOpen) return blocked(PickerIssueCodes.NotReady)
        blockingIssue()?.let { return SubmitEvaluation.Blocked(it) }
        if (pendingRequest != null) return blocked(PickerIssueCodes.CommitNotAccepted)
        if (inputValue.composition != null) return blocked(PickerIssueCodes.Incomplete)

        val evaluation = if (isDirty || directEdited) {
            config.evaluateText(inputValue, currentValue, now, directEdited)
        } else {
            currentValue?.let { config.validateExternal(it, now) }
                ?: if (config.required) blocked(PickerIssueCodes.Required) else SubmitEvaluation.Ready(null)
        }
        return evaluation
    }

    /**
     * 显示给定校验问题；未提供时显示当前就绪问题。
     */
    fun showValidation(validationIssue: PickerIssue? = null) {
        issue = validationIssue ?: issue ?: PickerIssue(PickerIssueCodes.NotReady)
    }

    /**
     * 请求在下次外部值绑定时执行受控重置。
     *
     * 可在所有者重组前从事件处理器安全调用；新值可用后由 [bind] 完成重置。
     */
    fun resetEditing() {
        pointerActionPending = false
        resetRequested = true
        pendingRequest = null
        issue = null
        isPopupOpen = false
        panelDraftValue = null
        panelRawInputValue = null
        isDirty = false
        directEdited = false
        panelEdited = false
    }

    /**
     * 打开草稿会话，并将无效编辑文本保留为整体输入。
     */
    internal fun openPanel() {
        val config = configuration ?: return
        if (isPopupOpen) return
        pointerActionPending = false
        panelInputSnapshot = inputValue
        panelDirtySnapshot = isDirty
        panelDirectEditedSnapshot = directEdited
        val parsed = config.parseText(inputValue.text)
        val hasEditedText = directEdited || isDirty
        // 完整值可初始化分段控件；已编辑但无效的值须保持整体，避免打开面板时猜测并替换用户输入。
        val rawInput = if (
            hasEditedText &&
            inputValue.text.isNotEmpty() &&
            parsed !is ParseResult.Parsed
        ) {
            inputValue
        } else {
            null
        }
        panelRawInputValue = rawInput
        val baseValue = when {
            parsed is ParseResult.Parsed -> parsed.value
            rawInput != null -> null
            hasEditedText && parsed is ParseResult.Empty -> null
            else -> currentValue
        }
        panelDraftValue = config.createDraft(baseValue)
        panelEdited = false
        isPopupOpen = true
        issue = null
    }

    /**
     * 恢复当前面板打开时捕获的字段快照。
     */
    internal fun closePanelWithoutApplying() {
        if (!isPopupOpen) return
        inputValue = panelInputSnapshot
        isDirty = panelDirtySnapshot
        directEdited = panelDirectEditedSnapshot
        panelDraftValue = null
        panelRawInputValue = null
        panelEdited = false
        isPopupOpen = false
        issue = null
    }

    /**
     * 丢弃本地编辑并载入最新外部值。
     */
    fun loadExternalValue() {
        if (!isBound) return
        resetFromExternal(currentValue)
        pendingRequest = null
        resetRequested = false
        isPopupOpen = false
    }

    /**
     * 保留本地编辑，并基于最新环境和值重建上下文。
     */
    fun keepEditing() {
        val config = configuration ?: return
        if (!isBound) return
        observedValue = currentValue
        contextKey = config.contextKey
        panelDraftValue = config.rebaseDraft(panelDraftValue)
        pendingRequest = null
        issue = null
    }

    /**
     * 替换面板草稿，并可选地标记为已编辑。
     */
    internal fun updatePanelDraft(draft: Any?, edited: Boolean = true) {
        if (!isPopupOpen) return
        panelDraftValue = draft
        panelRawInputValue = null
        if (edited) {
            panelEdited = true
            isDirty = true
            issue = null
        }
    }

    /**
     * 原样更新无效或未完成的面板整体输入。
     */
    internal fun updatePanelRawInput(input: TextFieldValue) {
        if (!isPopupOpen) return
        panelRawInputValue = input
        panelEdited = true
        isDirty = true
        issue = null
    }

    /**
     * 整体输入解析成功后切换为分段编辑。
     */
    internal fun replacePanelRawInput(draft: Any?) {
        if (!isPopupOpen) return
        panelRawInputValue = null
        panelDraftValue = draft
        panelEdited = true
        isDirty = true
        issue = null
    }

    /**
     * 清空活动面板草稿，但不关闭会话。
     */
    internal fun clearPanel() {
        val config = configuration ?: return
        if (isPopupOpen) updatePanelDraft(config.createDraft(null))
    }

    /**
     * 校验当前面板候选值并向所有者请求提交。
     */
    internal fun applyPanel(now: PickerNow): SubmitEvaluation<T> {
        val evaluation = evaluatePanel(now)
        if (evaluation is SubmitEvaluation.Blocked) {
            issue = evaluation.issue
            return evaluation
        }
        val ready = evaluation as SubmitEvaluation.Ready
        requestCandidate(ready.value, closesPanel = true)
        return evaluation
    }

    /**
     * 评估活动原始输入或分段草稿，但不提交。
     */
    internal fun evaluatePanel(now: PickerNow): SubmitEvaluation<T> {
        val config = configuration ?: return blocked(PickerIssueCodes.NotReady)
        if (!isPopupOpen) return blocked(PickerIssueCodes.NotReady)
        blockingIssue()?.let { return SubmitEvaluation.Blocked(it) }
        if (pendingRequest != null) return blocked(PickerIssueCodes.CommitNotAccepted)
        // 将原始输入作为整体评估，既保留精确文本，也让编解码器区分未完成与无效输入。
        panelRawInputValue?.let { rawInput ->
            return config.evaluateText(rawInput, currentValue, now, true)
        }
        return config.evaluateDraft(panelDraftValue, currentValue, now, panelEdited)
    }

    /**
     * 判断当前面板候选值能否应用。
     */
    internal fun canApply(now: PickerNow): Boolean =
        evaluatePanel(now) is SubmitEvaluation.Ready

    /**
     * 保存字段直接输入，并保留光标与输入法组合元数据。
     */
    internal fun updateInput(value: TextFieldValue) {
        val contentChanged = value.text != inputValue.text || value.composition != inputValue.composition
        inputValue = value
        if (contentChanged) {
            isDirty = true
            directEdited = true
            issue = null
        }
    }

    /**
     * 从字段直接输入中评估并请求候选值。
     */
    internal fun submitDirect(now: PickerNow): SubmitEvaluation<T> {
        val evaluation = evaluateForDirect(now)
        if (evaluation is SubmitEvaluation.Blocked) {
            issue = evaluation.issue
        } else {
            val ready = evaluation as SubmitEvaluation.Ready
            requestCandidate(ready.value, closesPanel = false)
        }
        return evaluation
    }

    /**
     * 清空直接输入；字段可选时请求空值。
     */
    internal fun clearDirect(): SubmitEvaluation<T> {
        val config = configuration ?: return blocked(PickerIssueCodes.NotReady)
        pointerActionPending = false
        inputValue = TextFieldValue()
        isDirty = true
        directEdited = true
        issue = null
        val evaluation = if (config.required) {
            blocked(PickerIssueCodes.Required)
        } else {
            SubmitEvaluation.Ready(null)
        }
        if (evaluation is SubmitEvaluation.Ready) {
            requestCandidate(null, closesPanel = false)
        } else if (evaluation is SubmitEvaluation.Blocked) {
            issue = evaluation.issue
        }
        return evaluation
    }

    /**
     * 请求外部提交；请求来自弹层时，在被接受前保持面板打开。
     */
    internal fun requestCandidate(candidate: T?, closesPanel: Boolean) {
        if (configuration == null) return
        if (candidate == currentValue && pendingRequest == null) {
            inputValue = formatValue(candidate)
            isDirty = false
            directEdited = false
            panelEdited = false
            if (closesPanel) {
                isPopupOpen = false
                panelDraftValue = null
                panelRawInputValue = null
            }
            issue = null
            return
        }
        pendingRequest = PendingRequest(
            candidate = candidate,
            originalValue = currentValue,
            closesPanel = closesPanel,
        )
        currentOnValueChange(candidate)
    }

    /**
     * 将已提交值格式化为光标位于末尾的字段值。
     */
    internal fun formatValue(value: T?): TextFieldValue {
        val config = configuration ?: return TextFieldValue()
        return textFieldValue(value?.let(config.formatValue) ?: "")
    }

    private fun evaluateForDirect(now: PickerNow): SubmitEvaluation<T> {
        val config = configuration ?: return blocked(PickerIssueCodes.NotReady)
        if (resetRequested || isPopupOpen) return blocked(PickerIssueCodes.NotReady)
        blockingIssue()?.let { return SubmitEvaluation.Blocked(it) }
        if (pendingRequest != null) return blocked(PickerIssueCodes.CommitNotAccepted)
        if (inputValue.composition != null) return blocked(PickerIssueCodes.Incomplete)
        return config.evaluateText(inputValue, currentValue, now, directEdited)
    }

    private fun blockingIssue(): PickerIssue? =
        issue?.takeIf {
            it.code in setOf(
                PickerIssueCodes.ExternalConflict,
                PickerIssueCodes.ZoneConflict,
                PickerIssueCodes.PrecisionConflict,
                PickerIssueCodes.CommitNotAccepted,
            )
        }

    private fun synchronizeContext(config: FieldConfiguration<T>) {
        if (contextKey == config.contextKey) return
        val wasDirty = isDirty || directEdited || panelEdited || isPopupOpen
        contextKey = config.contextKey
        if (wasDirty) {
            issue = PickerIssue(contextIssueCode(config.kind))
        } else {
            inputValue = formatValue(config.value)
        }
    }

    private fun synchronizeExternalValue(value: T?) {
        val pending = pendingRequest
        if (pending != null) {
            if (value == pending.candidate) {
                pendingRequest = null
                currentValue = value
                observedValue = value
                inputValue = formatValue(value)
                isDirty = false
                directEdited = false
                panelEdited = false
                issue = null
                if (pending.closesPanel) {
                    isPopupOpen = false
                    panelDraftValue = null
                    panelRawInputValue = null
                }
                return
            }
            if (value == pending.originalValue) {
                if (pending.checked) {
                    pendingRequest = null
                    issue = PickerIssue(PickerIssueCodes.CommitNotAccepted)
                } else {
                    pending.checked = true
                }
            } else {
                pendingRequest = null
                currentValue = value
                observedValue = value
                handleExternalConflict(value)
            }
            return
        }

        if (value == observedValue) {
            currentValue = value
            return
        }
        if (isDirty || directEdited || panelEdited || isPopupOpen) {
            handleExternalConflict(value)
        } else {
            currentValue = value
            observedValue = value
            inputValue = formatValue(value)
            issue = null
        }
    }

    private fun handleExternalConflict(value: T?) {
        currentValue = value
        issue = PickerIssue(
            PickerIssueCodes.ExternalConflict,
            mapOf("value" to (value?.toString() ?: "null")),
        )
    }

    private fun resetFromExternal(value: T?) {
        currentValue = value
        observedValue = value
        inputValue = formatValue(value)
        isDirty = false
        directEdited = false
        panelEdited = false
        panelDraftValue = null
        panelRawInputValue = null
        issue = null
    }

    private fun contextIssueCode(kind: PickerFieldKind): String =
        when (kind) {
            PickerFieldKind.Instant -> PickerIssueCodes.ZoneConflict
            else -> PickerIssueCodes.PrecisionConflict
        }

    private fun blocked(code: String): SubmitEvaluation.Blocked =
        SubmitEvaluation.Blocked(PickerIssue(code))
}
