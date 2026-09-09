package picker.material

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import picker.desktop.PickerColors
import picker.desktop.PickerDefaults
import picker.desktop.PickerStyle
import picker.desktop.PickerTheme
import picker.desktop.PickerTypography

/**
 * 将当前 Material 主题映射为选择器设计系统。
 *
 * 选择器仍以 Foundation 原语渲染；Material 只在此边界提供应用颜色、排版和形状。
 */
@Composable
fun MaterialPickerTheme(
    /**
     * 是否用深色 Material 角色作为选择器回退值来源。
     */
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * 使用 Material 颜色、排版与形状渲染的选择器内容。
     */
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val shapes = MaterialTheme.shapes
    val pickerColors = PickerColors(
        surface = colors.surface,
        onSurface = colors.onSurface,
        mutedSurface = colors.surfaceVariant,
        onMuted = colors.onSurfaceVariant,
        selected = colors.primary,
        onSelected = colors.onPrimary,
        hoverSurface = colors.primaryContainer,
        outline = colors.outline,
        focus = colors.primary,
        error = colors.error,
        errorSurface = colors.errorContainer,
        disabled = colors.onSurface.copy(alpha = 0.38f),
    )
    val pickerTypography = PickerTypography(
        body = typography.bodyMedium.copy(color = colors.onSurface),
        label = typography.labelLarge.copy(color = colors.onSurfaceVariant),
        caption = typography.bodySmall.copy(color = colors.onSurfaceVariant),
    )
    val fallback = if (darkTheme) PickerDefaults.darkStyle() else PickerDefaults.lightStyle()
    val style = PickerStyle(
        colors = pickerColors,
        typography = pickerTypography,
        dimensions = fallback.dimensions,
        shapes = fallback.shapes.copy(
            field = shapes.small,
            panel = shapes.medium,
            control = shapes.small,
        ),
        motion = fallback.motion,
    )
    PickerTheme(style = style, content = content)
}
