package picker.foundation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import picker.core.PickerEnvironment
import picker.core.TimeFormatOptions

/**
 * 暴露给自定义日期时间编辑器槽位的作用域。
 *
 * 日期和时间更新均通过 [updateDraft] 汇集，使调用方可渲染自定义控件而不绕过字段状态。
 */
class DateTimeEditorScope internal constructor(
    /**
     * 当前组合日期时间草稿。
     */
    val draft: DateTimeDraft,
    /**
     * 周围选择器会话使用的环境。
     */
    val environment: PickerEnvironment,
    /**
     * 时间部分的格式规则。
     */
    val format: TimeFormatOptions,
    /**
     * 替换组合草稿的回调。
     */
    val updateDraft: (DateTimeDraft) -> Unit,
) {
    /**
     * 仅替换当前草稿的日期部分。
     */
    fun updateDate(draft: DateDraft) {
        updateDraft(this.draft.copy(date = draft))
    }

    /**
     * 仅替换当前草稿的时间部分。
     */
    fun updateTime(draft: TimeDraft) {
        updateDraft(this.draft.copy(time = draft))
    }
}

/**
 * 暴露自定义槽位的底层日期时间编辑器，同时复用基础时间编辑器的键盘和输入法行为。
 */
@Composable
fun BasicDateTimeEditor(
    /**
     * 当前组合草稿。
     */
    draft: DateTimeDraft,
    /**
     * 接收每次被接受的草稿编辑。
     */
    onDraftChange: (DateTimeDraft) -> Unit,
    /**
     * 与编辑器关联的环境。
     */
    environment: PickerEnvironment,
    /**
     * 时间分段格式。
     */
    format: TimeFormatOptions,
    /**
     * 应用于基础编辑器容器的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 编辑器控件是否接受输入。
     */
    enabled: Boolean = true,
    /**
     * 控件是否可见但不可编辑。
     */
    readOnly: Boolean = false,
    /**
     * 在编辑器作用域中渲染的自定义内容。
     */
    content: @Composable DateTimeEditorScope.() -> Unit,
) {
    DateTimeEditorScope(
        draft = draft,
        environment = environment,
        format = format,
        updateDraft = onDraftChange,
    ).run {
        BasicTimeEditor(
            draft = draft.time,
            onDraftChange = ::updateTime,
            format = format,
            modifier = modifier,
            enabled = enabled,
            readOnly = readOnly,
        ) {
            content()
        }
    }
}
