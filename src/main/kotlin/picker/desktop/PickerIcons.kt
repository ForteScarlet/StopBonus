package picker.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * 默认选择器皮肤使用的内部矢量图标形状。
 */
internal enum class PickerIconKind {
    /**
     * 向左导航箭头。
     */
    ChevronLeft,
    /**
     * 向右导航箭头。
     */
    ChevronRight,
    /**
     * 字段弹层开关箭头。
     */
    ChevronDown,
    /**
     * 清空字段的叉号。
     */
    Clear,
    /**
     * 时间分段递增箭头。
     */
    ArrowUp,
    /**
     * 时间分段递减箭头。
     */
    ArrowDown,
}

/**
 * 绘制不依赖字体字形覆盖的小型矢量图标。
 *
 * [onPress] 在指针初始分派阶段执行，使字段能区分有意的清空/展开点击与普通失焦。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PickerIcon(
    /**
     * 要绘制的图标形状。
     */
    kind: PickerIconKind,
    /**
     * 辅助技术播报的名称。
     */
    contentDescription: String,
    /**
     * 是否允许指针与键盘激活。
     */
    enabled: Boolean = true,
    /**
     * 可选激活回调。
     */
    onClick: (() -> Unit)? = null,
    /**
     * 因指针按下即将改变焦点前执行的可选回调。
     */
    onPress: (() -> Unit)? = null,
    /**
     * 如“已展开”或“已收起”的可选状态描述。
     */
    stateDescription: String? = null,
    /**
     * 应用于图标点击目标的修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 提供图标尺寸与颜色的样式。
     */
    style: PickerStyle = PickerTheme.current(),
) {
    val iconModifier = modifier
        .size(style.dimensions.iconButtonMinSize)
        .then(
            if (onPress != null) {
                Modifier.onPointerEvent(
                    eventType = PointerEventType.Press,
                    pass = PointerEventPass.Initial,
                ) {
                    if (enabled) onPress()
                }
            } else {
                Modifier
            }
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(enabled = enabled, onClick = onClick)
            } else {
                Modifier
            }
        )
        .semantics {
            this.contentDescription = contentDescription
            if (onClick != null) role = Role.Button
            if (!enabled) disabled()
            stateDescription?.let { this.stateDescription = it }
        }

    Canvas(iconModifier) {
        val color = if (enabled) style.colors.onSurface else style.colors.disabled
        val strokeWidth = 1.8.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val halfWidth = size.width * 0.18f
        val halfHeight = size.height * 0.18f
        val cap = StrokeCap.Round
        when (kind) {
            PickerIconKind.ChevronLeft -> {
                drawLine(
                    color = color,
                    start = Offset(center.x + halfWidth, center.y - halfHeight),
                    end = Offset(center.x - halfWidth, center.y),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
                drawLine(
                    color = color,
                    start = Offset(center.x - halfWidth, center.y),
                    end = Offset(center.x + halfWidth, center.y + halfHeight),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
            }

            PickerIconKind.ChevronRight -> {
                drawLine(
                    color = color,
                    start = Offset(center.x - halfWidth, center.y - halfHeight),
                    end = Offset(center.x + halfWidth, center.y),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
                drawLine(
                    color = color,
                    start = Offset(center.x + halfWidth, center.y),
                    end = Offset(center.x - halfWidth, center.y + halfHeight),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
            }

            PickerIconKind.ChevronDown -> {
                drawLine(
                    color = color,
                    start = Offset(center.x - halfWidth, center.y - halfHeight / 2f),
                    end = Offset(center.x, center.y + halfHeight / 2f),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y + halfHeight / 2f),
                    end = Offset(center.x + halfWidth, center.y - halfHeight / 2f),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
            }

            PickerIconKind.Clear -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.32f, size.height * 0.32f),
                    end = Offset(size.width * 0.68f, size.height * 0.68f),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.68f, size.height * 0.32f),
                    end = Offset(size.width * 0.32f, size.height * 0.68f),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
            }

            PickerIconKind.ArrowUp -> {
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y - halfHeight),
                    end = Offset(center.x, center.y + halfHeight),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y - halfHeight),
                    end = Offset(center.x - halfWidth, center.y - halfHeight / 3f),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y - halfHeight),
                    end = Offset(center.x + halfWidth, center.y - halfHeight / 3f),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
            }

            PickerIconKind.ArrowDown -> {
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y + halfHeight),
                    end = Offset(center.x, center.y - halfHeight),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y + halfHeight),
                    end = Offset(center.x - halfWidth, center.y + halfHeight / 3f),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
                drawLine(
                    color = color,
                    start = Offset(center.x, center.y + halfHeight),
                    end = Offset(center.x + halfWidth, center.y + halfHeight / 3f),
                    strokeWidth = strokeWidth,
                    cap = cap,
                )
            }
        }
    }
}
