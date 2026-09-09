package picker.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import picker.core.HourCycle
import picker.foundation.TimeSegment
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 表盘当前编辑的时间单位。
 */
internal enum class TimeDialMode {
    /**
     * 选择小时。
     */
    Hour,

    /**
     * 选择分钟。
     */
    Minute,

    /**
     * 选择秒。
     */
    Second,
}

/**
 * 将表盘模式映射为同一份时间草稿中的分段。
 */
internal val TimeDialMode.segment: TimeSegment
    get() = when (this) {
        TimeDialMode.Hour -> TimeSegment.Hour
        TimeDialMode.Minute -> TimeSegment.Minute
        TimeDialMode.Second -> TimeSegment.Second
    }

/**
 * 绘制并操作默认皮肤的圆形时间表盘。
 *
 * 表盘负责快速定位小时及五分钟刻度，并可用滚轮逐格循环微调；上方数字窗、表盘
 * 与键盘始终写入同一份时间草稿。点击圆心会清除当前分段。
 */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun TimeDial(
    /**
     * 表盘当前显示的单位。
     */
    mode: TimeDialMode,
    /**
     * 当前 24 小时制的小时值。
     */
    hour24: Int?,
    /**
     * 当前分钟值。
     */
    minute: Int?,
    /**
     * 当前秒值；秒级精度未启用或尚未选择时为空。
     */
    second: Int?,
    /**
     * 小时制决定表盘是否显示内外两圈小时。
     */
    hourCycle: HourCycle,
    /**
     * 是否响应指针操作。
     */
    enabled: Boolean,
    /**
     * 选中小时后的回调。
     */
    onHourSelected: (Int) -> Unit,
    /**
     * 选中分钟后的回调。
     */
    onMinuteSelected: (Int) -> Unit,
    /**
     * 选中秒后的回调。
     */
    onSecondSelected: (Int) -> Unit,
    /**
     * 清除当前表盘分段后的回调。
     */
    onValueCleared: (TimeDialMode) -> Unit,
    /**
     * 将当前表盘分段循环移动一格。
     */
    onIncrement: (TimeDialMode, Int) -> Unit,
    /**
     * 皮肤提供的颜色与尺寸。
     */
    style: PickerStyle,
    /**
     * 外部布局修饰符。
     */
    modifier: Modifier = Modifier,
    /**
     * 表盘直径。
     */
    diameter: Dp = 216.dp,
) {
    val latestHourSelected by rememberUpdatedState(onHourSelected)
    val latestMinuteSelected by rememberUpdatedState(onMinuteSelected)
    val latestSecondSelected by rememberUpdatedState(onSecondSelected)
    val latestValueCleared by rememberUpdatedState(onValueCleared)
    val latestIncrement by rememberUpdatedState(onIncrement)
    val textMeasurer = rememberTextMeasurer()
    val selected = when (mode) {
        TimeDialMode.Hour -> hour24
        TimeDialMode.Minute -> minute
        TimeDialMode.Second -> second
    }
    val divisionCount = if (mode == TimeDialMode.Hour) 12 else 60
    val rawIndex = when (mode) {
        TimeDialMode.Hour -> (hour24?.rem(12) ?: 0).toFloat()
        TimeDialMode.Minute -> (minute ?: 0).toFloat()
        TimeDialMode.Second -> (second ?: 0).toFloat()
    }
    var targetIndex by remember(mode) { mutableStateOf(rawIndex) }
    LaunchedEffect(rawIndex, divisionCount) {
        targetIndex = nearestCircularIndex(targetIndex, rawIndex, divisionCount)
    }
    val animatedIndex by animateFloatAsState(
        targetValue = targetIndex,
        label = "表盘指针过渡",
    )
    val isInnerHour = mode == TimeDialMode.Hour &&
            hourCycle == HourCycle.H24 &&
            hour24 in 1..12
    val animatedSelectionRadius by animateFloatAsState(
        targetValue = when {
            selected == null -> 0f
            isInnerHour -> 0.255f
            else -> 0.42f
        },
        label = "表盘内外圈过渡",
    )
    val animatedHandleRadius by animateFloatAsState(
        // 内圈使用更轻巧的选中圆，避免遮住相邻小时刻度。
        targetValue = if (isInnerHour) 0.075f else 0.11f,
        label = "表盘选中圆过渡",
    )
    Canvas(
        modifier = modifier
            .size(diameter)
            .pointerInput(mode, hourCycle, enabled) {
                detectTapGestures { point ->
                    if (!enabled) return@detectTapGestures
                    if (
                        hypot(point.x - size.width / 2f, point.y - size.height / 2f) <=
                        minOf(size.width, size.height).toFloat() / 8f
                    ) {
                        latestValueCleared(mode)
                        return@detectTapGestures
                    }
                    when (mode) {
                        TimeDialMode.Hour -> latestHourSelected(
                            hourFromPoint(point, size.width.toFloat(), size.height.toFloat(), hourCycle),
                        )

                        TimeDialMode.Minute -> latestMinuteSelected(
                            minuteFromPoint(point, size.width.toFloat(), size.height.toFloat()),
                        )

                        TimeDialMode.Second -> latestSecondSelected(
                            minuteFromPoint(point, size.width.toFloat(), size.height.toFloat()),
                        )
                    }
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                if (!enabled) return@onPointerEvent
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) latestIncrement(mode, if (delta < 0f) -1 else 1)
            }
            .onPointerEvent(PointerEventType.Press) { event ->
                if (enabled && event.buttons.isPressed(PointerButton.Tertiary.index)) latestValueCleared(mode)
            }
            .semantics {
                contentDescription = if (mode == TimeDialMode.Hour) "圆形表盘：选择小时" else "圆形表盘：选择分钟"
                role = Role.Button
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension * 0.42f
        val innerRadius = size.minDimension * 0.255f
        val selectedRadius = size.minDimension * animatedSelectionRadius
        val selectedPoint = clockPoint(center, selectedRadius, animatedIndex, divisionCount)

        drawCircle(color = style.colors.mutedSurface, radius = size.minDimension / 2f)
        drawLine(
            color = style.colors.selected,
            start = center,
            end = selectedPoint,
            strokeWidth = 2.dp.toPx(),
        )
        drawCircle(
            color = style.colors.selected,
            radius = size.minDimension * animatedHandleRadius,
            center = selectedPoint,
        )
        drawCircle(color = style.colors.selected, radius = 3.dp.toPx(), center = center)

        when (mode) {
            TimeDialMode.Hour -> drawHours(
                center = center,
                outerRadius = outerRadius,
                innerRadius = innerRadius,
                hourCycle = hourCycle,
                selected = selected,
                selectedOnInnerRing = isInnerHour,
                style = style,
                textMeasurer = textMeasurer,
            )

            TimeDialMode.Minute, TimeDialMode.Second -> (0 until 12).forEach { index ->
                val value = index * 5
                val point = clockPoint(center, outerRadius, value, 60)
                drawDialLabel(
                    textMeasurer = textMeasurer,
                    text = value.toString().padStart(2, '0'),
                    center = point,
                    color = if (value == selected) style.colors.onSelected else style.colors.onMuted,
                    style = style.typography.label,
                )
            }
        }
        drawCircle(
            color = style.colors.outline.copy(alpha = 0.36f),
            radius = size.minDimension / 2f,
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

private fun DrawScope.drawHours(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    hourCycle: HourCycle,
    selected: Int?,
    selectedOnInnerRing: Boolean,
    style: PickerStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
) {
    (0 until 12).forEach { index ->
        if (hourCycle == HourCycle.H24) {
            val outerValue = if (index == 0) 0 else index + 12
            val outerPoint = clockPoint(center, outerRadius, index, 12)
            drawDialLabel(
                textMeasurer,
                outerValue.toString().padStart(2, '0'),
                outerPoint,
                if (selected == outerValue && !selectedOnInnerRing) {
                    style.colors.onSelected
                } else {
                    style.colors.onMuted
                },
                style.typography.label,
            )
            val innerValue = if (index == 0) 12 else index
            val innerPoint = clockPoint(center, innerRadius, index, 12)
            drawDialLabel(
                textMeasurer,
                innerValue.toString().padStart(2, '0'),
                innerPoint,
                if (selected == innerValue && selectedOnInnerRing) style.colors.onSelected else style.colors.onMuted,
                style.typography.caption,
            )
        } else {
            val value = if (index == 0) 12 else index
            drawDialLabel(
                textMeasurer,
                value.toString().padStart(2, '0'),
                clockPoint(center, outerRadius, index, 12),
                if (selected?.rem(12) == index) style.colors.onSelected else style.colors.onMuted,
                style.typography.label,
            )
        }
    }
}

private fun DrawScope.drawDialLabel(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    center: Offset,
    color: androidx.compose.ui.graphics.Color,
    style: TextStyle,
) {
    val layout = textMeasurer.measure(AnnotatedString(text), style.copy(color = color))
    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

private fun clockPoint(center: Offset, radius: Float, index: Number, count: Int): Offset {
    val angle = index.toDouble() * (2 * PI / count) - PI / 2
    return Offset(
        x = center.x + cos(angle).toFloat() * radius,
        y = center.y + sin(angle).toFloat() * radius,
    )
}

internal fun hourFromPoint(point: Offset, width: Float, height: Float, hourCycle: HourCycle): Int {
    val center = Offset(width / 2f, height / 2f)
    val index = clockIndex(point, center)
    if (hourCycle == HourCycle.H12) return if (index == 0) 12 else index
    val innerRing = hypot(point.x - center.x, point.y - center.y) < width * 0.34f
    return if (innerRing) if (index == 0) 12 else index else if (index == 0) 0 else index + 12
}

internal fun minuteFromPoint(point: Offset, width: Float, height: Float): Int =
    clockIndex(point, Offset(width / 2f, height / 2f)) * 5

private fun clockIndex(point: Offset, center: Offset): Int {
    val angle = atan2(point.y - center.y, point.x - center.x) + PI / 2
    return Math.floorMod((angle / (2 * PI) * 12).roundToInt(), 12)
}

/**
 * 将 [target] 展开到最接近 [current] 的环形等分位置，避免跨越零点时绕远路旋转。
 */
internal fun nearestCircularIndex(current: Float, target: Float, divisionCount: Int): Float {
    require(divisionCount > 0) { "表盘等分数量必须为正数" }
    val division = divisionCount.toFloat()
    val whole = current.toInt()
    val currentPhase = Math.floorMod(whole, divisionCount).toFloat() + (current - whole)
    var distance = target - currentPhase
    if (distance > division / 2f) distance -= division
    if (distance < -division / 2f) distance += division
    return current + distance
}
