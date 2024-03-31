@file:Suppress("FunctionName")

import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import database.connectDatabaseOperator
import kotlinx.coroutines.Dispatchers
import org.slf4j.LoggerFactory
import view.App
import view.AppState
import java.awt.Toolkit
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

fun storeAppPath(): Path {
    if (System.getenv("DEBUG").toBoolean() || System.getProperty("debug").toBoolean()) {
        return Path("./data")
    }

    val localAppData = System.getenv("LOCALAPPDATA")
    if (localAppData != null) {
        return Path(localAppData, "StopBonus", "data")
    }

    val userHome = System.getProperty("user.home")
    if (userHome != null) {
        return Path(userHome, "StopBonus", "data")
    }

    return Path("./data")
}


@Composable
fun Logo() = painterResource("icons/angry face with horns.svg")

private val fontBTT = Font("font/BTT.ttf")

fun FontBTT() = fontBTT
val FontBTTFamily = FontFamily(FontBTT())

private val fontLXGWNeoXiHeiScreen = Font("font/LXGWNeoXiHeiScreen.ttf")

/**
 * 霞鹜新晰黑屏幕阅读版
 */
fun FontLXGWNeoXiHeiScreen() = fontLXGWNeoXiHeiScreen
val FontLXGWNeoXiHeiScreenFamily = FontFamily(FontLXGWNeoXiHeiScreen())

private val logger = LoggerFactory.getLogger("MAIN")

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        logger.error("UncaughtExceptionHandler on Thread[{}]", t, e)
    }

    val databaseOp = connectDatabaseOperator(dataDir = storeAppPath(), schemaName = "bonus")

    application {
        val scope = rememberCoroutineScope { Dispatchers.Default }
        val materialThemeState = rememberMaterialThemeState()

        val winSize = kotlin.runCatching {
            with(Toolkit.getDefaultToolkit().screenSize) {
                DpSize((width * 0.8f).dp, (height * 0.8f).dp)
            }
        }.getOrElse {
            DpSize(1024.dp, 768.dp)
        }

        val winState = rememberWindowState(size = winSize)
        val trayState = rememberTrayState()

        if (winState.isMinimized) {
            Tray(
                icon = Logo(),
                tooltip = "别奖励了😡",
                state = trayState,
                onAction = {
                    winState.isMinimized = false
                    winState.position = WindowPosition.PlatformDefault
                },
                menu = {
                    Item("Open") {
                        winState.isMinimized = false
                        winState.position = WindowPosition.PlatformDefault
                    }
                    Separator()
                    Item("Exit") {
                        exitApplication()
                    }
                }
            )
        }

        Window(
            icon = Logo(),
            state = winState,
            title = "别奖励了!",
            visible = !winState.isMinimized,
            enabled = !winState.isMinimized,
            onCloseRequest = {
                exitApplication()
            }
        ) {
            MaterialTheme(
                colors = materialThemeState.colors,
                typography = materialThemeState.typography,
                shapes = materialThemeState.shapes
            ) {
                App(
                    remember {
                        AppState(
                            winState,
                            materialThemeState,
                            scope,
                            databaseOp
                        )
                    }
                )
            }
        }

    }
}

@Suppress("MemberVisibilityCanBePrivate", "unused")
class MaterialThemeState(
    val colorsState: MutableState<Colors>,
    val typographyState: MutableState<Typography>,
    val shapesState: MutableState<Shapes>,
) {
    var colors: Colors by colorsState
    var typography: Typography by typographyState
    var shapes: Shapes by shapesState
}

@Composable
private fun newTypography(): Typography {
    val fontBTT = FontFamily(FontBTT()) // , SystemFont("serif")
    val fontLXGW = FontFamily(FontLXGWNeoXiHeiScreen())
    // val family = FontFamily(FontBTT(), FontLXGWNeoXiHeiScreen())
    val def = MaterialTheme.typography
    return def.copy(
        h1 = def.h1.copy(fontFamily = fontBTT),
        h2 = def.h2.copy(fontFamily = fontBTT),
        h3 = def.h3.copy(fontFamily = fontBTT),
        h4 = def.h4.copy(fontFamily = fontLXGW),
        h5 = def.h5.copy(fontFamily = fontLXGW),
        h6 = def.h6.copy(fontFamily = fontLXGW),
        subtitle1 = def.subtitle1.copy(fontFamily = fontLXGW),
        subtitle2 = def.subtitle2.copy(fontFamily = fontLXGW),
        body1 = def.body1.copy(fontFamily = fontLXGW),
        body2 = def.body2.copy(fontFamily = fontLXGW),
        button = def.button.copy(fontFamily = fontLXGW),
        caption = def.caption.copy(fontFamily = fontLXGW),
        overline = def.overline.copy(fontFamily = fontLXGW),
    )
}

@Composable
fun rememberMaterialThemeState(
    initColors: Colors = MaterialTheme.colors,
    initTypography: Typography = newTypography(),
    initShapes: Shapes = MaterialTheme.shapes
): MaterialThemeState {
    val colors = remember { mutableStateOf(initColors) }
    val typography = remember { mutableStateOf(initTypography) }
    val shapes = remember { mutableStateOf(initShapes) }

    return remember {
        MaterialThemeState(
            colors,
            typography,
            shapes,
        )
    }
}
