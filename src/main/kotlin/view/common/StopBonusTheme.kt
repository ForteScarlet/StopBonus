package view.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Shapes as Material3Shapes
import androidx.compose.material3.Typography as Material3Typography

private val fontBTT = Font("font/BTT.ttf")
private val fontLXGWNeoXiHeiScreen = Font("font/LXGWNeoXiHeiScreen.ttf")

private val fontBTTFamily = FontFamily(fontBTT)
private val fontLXGWNeoXiHeiScreenFamily = FontFamily(fontLXGWNeoXiHeiScreen)

private val StopBonusLightColorScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF2563EB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE8FF),
    onSecondaryContainer = Color(0xFF0B1F42),
    tertiary = Color(0xFFB45309),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE0C2),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF6F7F9),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF5EEAD4),
    surfaceTint = Color(0xFFFFFFFF),
    scrim = Color(0x66000000),
)

private val StopBonusDarkColorScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF93C5FD),
    onSecondary = Color(0xFF0B1F42),
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDCE8FF),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF2C1600),
    tertiaryContainer = Color(0xFF92400E),
    onTertiaryContainer = Color(0xFFFFE0C2),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF0F172A),
    inversePrimary = Color(0xFF0F766E),
    surfaceTint = Color(0xFF5EEAD4),
    scrim = Color(0x99000000),
)

private val StopBonusShapes = Material3Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val StopBonusTypography = Material3Typography().let { def ->
    def.copy(
        displayLarge = def.displayLarge.copy(fontFamily = fontBTTFamily),
        displayMedium = def.displayMedium.copy(fontFamily = fontBTTFamily),
        displaySmall = def.displaySmall.copy(fontFamily = fontBTTFamily),
        headlineLarge = def.headlineLarge.copy(fontFamily = fontBTTFamily),
        headlineMedium = def.headlineMedium.copy(fontFamily = fontBTTFamily),
        headlineSmall = def.headlineSmall.copy(fontFamily = fontLXGWNeoXiHeiScreenFamily),
        titleLarge = def.titleLarge.copy(fontFamily = fontLXGWNeoXiHeiScreenFamily),
        titleMedium = def.titleMedium.copy(fontFamily = fontLXGWNeoXiHeiScreenFamily),
        titleSmall = def.titleSmall.copy(fontFamily = fontLXGWNeoXiHeiScreenFamily),
        bodyLarge = def.bodyLarge.copy(fontFamily = fontLXGWNeoXiHeiScreenFamily),
        bodyMedium = def.bodyMedium.copy(fontFamily = fontLXGWNeoXiHeiScreenFamily),
        bodySmall = def.bodySmall.copy(fontFamily = fontLXGWNeoXiHeiScreenFamily),
        labelLarge = def.labelLarge.copy(fontFamily = fontLXGWNeoXiHeiScreenFamily),
        labelMedium = def.labelMedium.copy(fontFamily = fontLXGWNeoXiHeiScreenFamily),
        labelSmall = def.labelSmall.copy(fontFamily = fontLXGWNeoXiHeiScreenFamily),
    )
}

@Composable
fun StopBonusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) StopBonusDarkColorScheme else StopBonusLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = StopBonusTypography,
        shapes = StopBonusShapes,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content
        )
    }
}

/* https://grabient.com/HQNhE4BpgFhBmaIDsJowIzOvC0BMArIUhvtKjBRgByQaw1TDwAMr0NM+QA?angle=45 */
/*
background: linear-gradient(45deg, #4159d0 0.000%, #c84fc0 50.000%, #ffcd70 100.000%);
 */