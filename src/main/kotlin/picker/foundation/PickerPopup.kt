package picker.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 尽量把选择器弹层放在锚点下方，否则放在上方，并限制在窗口边距内。
 */
class PickerPopupPositionProvider(
    /**
     * 锚点与弹层之间的物理像素间距。
     */
    private val gapPx: Int,
    /**
     * 距各窗口边缘的最小物理像素距离。
     */
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val availableBelow = windowSize.height - anchorBounds.bottom - marginPx
        val availableAbove = anchorBounds.top - marginPx
        // 优先选择空间足够的一侧；两侧均不足时选择较宽的一侧，并在下方统一限制位置。
        val y = if (
            availableBelow >= popupContentSize.height ||
            availableBelow >= availableAbove
        ) {
            anchorBounds.bottom + gapPx
        } else {
            anchorBounds.top - popupContentSize.height - gapPx
        }

        val preferredX = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val minX = marginPx
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(minX)
        val minY = marginPx
        val maxY = (windowSize.height - popupContentSize.height - marginPx).coerceAtLeast(minY)
        return IntOffset(
            x = preferredX.coerceIn(minX, maxX),
            y = y.coerceIn(minY, maxY),
        )
    }
}

/**
 * 记住一个感知屏幕密度的弹层定位器。
 */
@Composable
fun rememberPickerPopupPositionProvider(
    /**
     * 字段与弹层之间的间距。
     */
    popupGap: Dp = 6.dp,
    /**
     * 弹层距应用窗口边缘的最小间距。
     */
    windowMargin: Dp = 8.dp,
): PickerPopupPositionProvider {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(density, popupGap, windowMargin) {
        PickerPopupPositionProvider(
            gapPx = with(density) { popupGap.roundToPx() },
            marginPx = with(density) { windowMargin.roundToPx() },
        )
    }
}

/**
 * 渲染可聚焦、可点击外部关闭并显式处理 Escape 的选择器弹层。
 *
 * 不把返回操作委托给平台弹层，因为选择器会话须通过 [onEscape] 恢复字段快照。
 */
@Composable
fun PickerPopup(
    /**
     * 用户点击弹层外部时调用。
     */
    onDismissRequest: () -> Unit,
    /**
     * 计算弹层相对字段锚点的位置。
     */
    positionProvider: PopupPositionProvider,
    /**
     * 由弹层的 Escape 处理器调用。
     */
    onEscape: () -> Unit = onDismissRequest,
    /**
     * 允许父视图在取消会话前消费 Escape。
     */
    shouldHandleEscape: () -> Boolean = { true },
    /**
     * 弹层内容。
     */
    content: @Composable () -> Unit,
) {
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = true,
            clippingEnabled = true,
            usePlatformDefaultWidth = false,
        ),
        content = {
            Box(
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.Escape &&
                        shouldHandleEscape()
                    ) {
                        onEscape()
                        true
                    } else {
                        false
                    }
                },
            ) {
                content()
            }
        },
    )
}
