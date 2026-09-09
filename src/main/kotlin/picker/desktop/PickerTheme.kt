package picker.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 独立选择器皮肤使用的颜色角色。
 */
data class PickerColors(
    /**
     * 弹层与字段背景。
     */
    val surface: Color,
    /**
     * 主要文字与图标颜色。
     */
    val onSurface: Color,
    /**
     * 次级或非激活表面的背景。
     */
    val mutedSurface: Color,
    /**
     * 绘制在 [mutedSurface] 上的文字。
     */
    val onMuted: Color,
    /**
     * 选中态背景。
     */
    val selected: Color,
    /**
     * 绘制在 [selected] 上的文字。
     */
    val onSelected: Color,
    /**
     * 悬停或“今天”背景。
     */
    val hoverSurface: Color,
    /**
     * 默认边框颜色。
     */
    val outline: Color,
    /**
     * 键盘焦点描边颜色。
     */
    val focus: Color,
    /**
     * 错误文字与边框颜色。
     */
    val error: Color,
    /**
     * 错误辅助信息背景。
     */
    val errorSurface: Color,
    /**
     * 禁用控件的内容颜色。
     */
    val disabled: Color,
)

/**
 * 选择器字段、标签及辅助信息使用的文字样式。
 */
data class PickerTypography(
    /**
     * 主要字段与控件文字。
     */
    val body: TextStyle,
    /**
     * 标签文字。
     */
    val label: TextStyle,
    /**
     * 辅助与提示文字。
     */
    val caption: TextStyle,
)

/**
 * 字段、面板、控件与弹层定位的尺寸 token。
 */
data class PickerDimensions(
    /**
     * 字段外壳的最小高度。
     */
    val fieldMinHeight: Dp,
    /**
     * 日历面板的最小首选宽度。
     */
    val panelPreferredWidth: Dp,
    /**
     * 时间面板的最小首选宽度。
     */
    val timePanelPreferredWidth: Dp,
    /**
     * 面板内边距。
     */
    val panelPadding: Dp,
    /**
     * 日历格的最小宽高。
     */
    val cellMinSize: Dp,
    /**
     * 绘制图标的尺寸。
     */
    val iconSize: Dp,
    /**
     * 图标的最小点击目标。
     */
    val iconButtonMinSize: Dp,
    /**
     * 相邻控件间距。
     */
    val controlGap: Dp,
    /**
     * 面板逻辑区块间距。
     */
    val sectionGap: Dp,
    /**
     * 默认边框宽度。
     */
    val borderWidth: Dp,
    /**
     * 键盘焦点描边宽度。
     */
    val focusWidth: Dp,
    /**
     * 字段锚点与弹层的间距。
     */
    val popupGap: Dp,
    /**
     * 弹层距应用窗口的最小边距。
     */
    val windowMargin: Dp,
    /**
     * 弹层面板的阴影高度。
     */
    val panelElevation: Dp,
) {
    companion object {
        /**
         * 返回适合舒适操作的较大点击目标尺寸。
         */
        fun comfortable(): PickerDimensions = PickerDimensions(
            fieldMinHeight = 44.dp,
            panelPreferredWidth = 360.dp,
            timePanelPreferredWidth = 320.dp,
            panelPadding = 16.dp,
            cellMinSize = 44.dp,
            iconSize = 20.dp,
            iconButtonMinSize = 44.dp,
            controlGap = 10.dp,
            sectionGap = 16.dp,
            borderWidth = 1.dp,
            focusWidth = 2.dp,
            popupGap = 8.dp,
            windowMargin = 10.dp,
            panelElevation = 10.dp,
        )
    }
}

/**
 * 字段、弹层面板及小型控件使用的形状。
 */
data class PickerShapes(
    /**
     * 字段外壳形状。
     */
    val field: Shape,
    /**
     * 弹层面板形状。
     */
    val panel: Shape,
    /**
     * 日期/时间控件形状。
     */
    val control: Shape,
)

/**
 * 选择器皮肤使用的动画时长。
 */
data class PickerMotion(
    /**
     * 悬停过渡时长（毫秒）。
     */
    val hoverMillis: Int = 80,
    /**
     * 展开过渡时长（毫秒）。
     */
    val openMillis: Int = 120,
    /**
     * 关闭过渡时长（毫秒）。
     */
    val closeMillis: Int = 80,
) {
    companion object {
        /**
         * 适用于测试和减少动态效果场景的无动画配置。
         */
        val None: PickerMotion = PickerMotion(hoverMillis = 0, openMillis = 0, closeMillis = 0)
    }
}

/**
 * 选择器组件使用的完整视觉契约。
 */
data class PickerStyle(
    /**
     * 皮肤颜色角色。
     */
    val colors: PickerColors,
    /**
     * 皮肤文字样式。
     */
    val typography: PickerTypography,
    /**
     * 皮肤尺寸 token。
     */
    val dimensions: PickerDimensions,
    /**
     * 皮肤形状。
     */
    val shapes: PickerShapes,
    /**
     * 可选动画时长。
     */
    val motion: PickerMotion = PickerMotion(),
)

private val LocalPickerStyle = staticCompositionLocalOf {
    PickerDefaults.defaultStyle
}

/**
 * 为选择器样式与默认样式标记提供 CompositionLocal。
 */
object PickerTheme {
    /**
     * 独立默认值。组件未显式传入样式时使用最近的 [PickerTheme] 值。
     */
    val style: PickerStyle
        get() = PickerDefaults.defaultStyle

    /**
     * 返回最近 [PickerTheme] 作用域提供的样式。
     */
    @Composable
    fun current(): PickerStyle = LocalPickerStyle.current

    /**
     * 在不依赖 Material 的前提下向选择器后代提供 [style]。
     */
    @Composable
    operator fun invoke(
        /**
         * 提供给 [content] 的样式。
         */
        style: PickerStyle = PickerDefaults.lightStyle(),
        /**
         * 在样式作用域中渲染的组件。
         */
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(LocalPickerStyle provides style, content = content)
    }
}

/**
 * 独立浅色/深色选择器样式及 token 的工厂方法。
 */
object PickerDefaults {
    /**
     * 用于区分省略样式参数与显式样式的身份标记。
     */
    internal val defaultStyle: PickerStyle by lazy(::createLightStyle)

    /**
     * 返回默认浅色颜色角色。
     */
    fun lightColors(): PickerColors = PickerColors(
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F172A),
        mutedSurface = Color(0xFFF6F7F9),
        onMuted = Color(0xFF334155),
        selected = Color(0xFF0F766E),
        onSelected = Color(0xFFFFFFFF),
        hoverSurface = Color(0xFFE6F4F1),
        outline = Color(0xFF64748B),
        focus = Color(0xFF0F766E),
        error = Color(0xFFB91C1C),
        errorSurface = Color(0xFFFEF2F2),
        disabled = Color(0xFF64748B),
    )

    /**
     * 返回默认深色颜色角色。
     */
    fun darkColors(): PickerColors = PickerColors(
        surface = Color(0xFF0F172A),
        onSurface = Color(0xFFE2E8F0),
        mutedSurface = Color(0xFF1E293B),
        onMuted = Color(0xFFCBD5E1),
        selected = Color(0xFF5EEAD4),
        onSelected = Color(0xFF042F2E),
        hoverSurface = Color(0xFF163D3A),
        outline = Color(0xFF94A3B8),
        focus = Color(0xFF5EEAD4),
        error = Color(0xFFFCA5A5),
        errorSurface = Color(0xFF450A0A),
        disabled = Color(0xFF94A3B8),
    )

    /**
     * 返回使用 [fontFamily] 的选择器文字样式。
     */
    fun typography(fontFamily: FontFamily = FontFamily.SansSerif): PickerTypography =
        PickerTypography(
            body = TextStyle(
                fontFamily = fontFamily,
                fontSize = 14.sp,
                color = Color(0xFF0F172A),
            ),
            label = TextStyle(
                fontFamily = fontFamily,
                fontSize = 13.sp,
                color = Color(0xFF334155),
            ),
            caption = TextStyle(
                fontFamily = fontFamily,
                fontSize = 12.sp,
                color = Color(0xFF334155),
            ),
        )

    /**
     * 返回紧凑默认尺寸 token。
     */
    fun dimensions(): PickerDimensions = PickerDimensions(
        fieldMinHeight = 36.dp,
        panelPreferredWidth = 320.dp,
        timePanelPreferredWidth = 280.dp,
        panelPadding = 12.dp,
        cellMinSize = 36.dp,
        iconSize = 18.dp,
        iconButtonMinSize = 32.dp,
        controlGap = 8.dp,
        sectionGap = 12.dp,
        borderWidth = 1.dp,
        focusWidth = 2.dp,
        popupGap = 6.dp,
        windowMargin = 8.dp,
        panelElevation = 8.dp,
    )

    /**
     * 返回字段与控件的默认圆角形状。
     */
    fun shapes(): PickerShapes = PickerShapes(
        field = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        panel = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        control = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    )

    /**
     * 返回可复制后定制的独立浅色样式。
     */
    fun lightStyle(): PickerStyle = createLightStyle()

    private fun createLightStyle(): PickerStyle = PickerStyle(
        colors = lightColors(),
        typography = typography(),
        dimensions = dimensions(),
        shapes = shapes(),
    )

    /**
     * 返回可复制后定制的独立深色样式。
     */
    fun darkStyle(): PickerStyle = lightStyle().copy(
        colors = darkColors(),
        typography = typography().copy(
            body = typography().body.copy(color = darkColors().onSurface),
            label = typography().label.copy(color = darkColors().onMuted),
            caption = typography().caption.copy(color = darkColors().onMuted),
        ),
    )
}

@Composable
internal fun effectiveStyle(style: PickerStyle): PickerStyle {
    // 公共默认值是身份标记；在此解析后，局部 PickerTheme 可覆盖默认值而不影响显式样式。
    return if (style === PickerTheme.style) PickerTheme.current() else style
}
