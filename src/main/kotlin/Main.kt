@file:Suppress("FunctionName")

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import config.*
import database.connectDatabaseOperator
import kotlinx.coroutines.Dispatchers
import love.forte.bonus.bonus_self_desktop.generated.resources.BTT
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.angry_face_with_horns
import org.jetbrains.compose.resources.painterResource
import org.slf4j.LoggerFactory
import view.App
import view.AppState
import view.common.StopBonusTheme
import view.welcome.WelcomeNavHost
import java.awt.Toolkit
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path


private const val APPID = "love.forte.bonus.bonus_self_desktop"
private const val DATA_DIR_NAME = "StopBonus"

/**
 * 获取应用数据存储路径
 *
 * 优先级：
 * 1. DEBUG 模式下使用 ./data
 * 2. Windows: $USER_HOME/AppData/Local/StopBonus/data（避免 MSIX 虚拟化路径偏移）
 * 3. 其他: %LOCALAPPDATA%/StopBonus/data
 * 4. 再退回 $HOME/StopBonus/data
 * 5. 默认: ./data
 */
fun storeAppPath(): Path {
    if (System.getenv("DEBUG").toBoolean() || System.getProperty("debug").toBoolean()) {
        return Path("./data")
    }

    val userHome = System.getProperty("user.home")
    val isWindows = System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)

    if (isWindows && userHome != null) {
        // 直接使用用户主目录，避免安装器重定向 LOCALAPPDATA 至 Packages/<PackageId>/LocalCache
        return Path(userHome, "AppData", "Local", DATA_DIR_NAME, "data")
    }

    val localAppData = System.getenv("LOCALAPPDATA")
    if (localAppData != null) {
        return Path(localAppData, DATA_DIR_NAME, "data")
    }

    if (userHome != null) {
        return Path(userHome, DATA_DIR_NAME, "data")
    }

    return Path("./data")
}

/**
 * 将旧版（受 MSIX 重定向影响的）数据目录搬迁到新的真实目录，避免用户数据丢失。
 * 仅在目标目录为空时触发，避免覆盖用户已存在的数据。
 */
private fun migrateFromLegacyPathIfNeeded(target: Path) {
    val legacyLocalAppData = System.getenv("LOCALAPPDATA") ?: return
    val legacyPath = Path(legacyLocalAppData, DATA_DIR_NAME, "data").toAbsolutePath().normalize()
    val targetPath = target.toAbsolutePath().normalize()

    if (legacyPath == targetPath) return
    if (!Files.exists(legacyPath)) return
    if (Files.exists(targetPath) && Files.list(targetPath).use { it.findAny().isPresent }) return

    runCatching {
        Files.createDirectories(targetPath)
        Files.walk(legacyPath).use { stream ->
            stream.forEach { source ->
                val dest = targetPath.resolve(legacyPath.relativize(source))
                if (Files.isDirectory(source)) {
                    Files.createDirectories(dest)
                } else {
                    Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        logger.info("migrate data: {} -> {}", legacyPath, targetPath)
    }.onFailure { e ->
        logger.warn("migrate data failed: {} -> {}", legacyPath, targetPath, e)
    }
}


@Composable
fun Logo(): Painter = painterResource(Res.drawable.angry_face_with_horns)

@Composable
fun FontBTT() = org.jetbrains.compose.resources.Font(Res.font.BTT)

@Composable
fun FontBTTFamily() = FontFamily(FontBTT())

private val fontLXGWNeoXiHeiScreen = Font("font/LXGWNeoXiHeiScreen.ttf")

/**
 * 霞鹜新晰黑屏幕阅读版
 */
@Composable
fun FontLXGWNeoXiHeiScreen() = fontLXGWNeoXiHeiScreen

@Composable
fun FontLXGWNeoXiHeiScreenFamily() = FontFamily(FontLXGWNeoXiHeiScreen())

private val logger = LoggerFactory.getLogger("MAIN")

/**
 * 应用程序入口点
 *
 * 初始化流程：
 * 1. 设置全局异常处理器
 * 2. 连接 H2 数据库
 * 3. 启动 Compose Desktop 窗口
 */
fun main() {
    // 设置全局未捕获异常处理器
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        logger.error("UncaughtExceptionHandler on Thread[{}]", t, e)
    }

    // 加载应用配置
    val initialConfig = ConfigManager.load()

    // 初始化全局 Clock 提供者
    ClockProvider.initialize(initialConfig.zoneId())

    // 初始化数据库连接
    val dataPath = storeAppPath()
    migrateFromLegacyPathIfNeeded(dataPath)
    val databaseOp = connectDatabaseOperator(dataDir = dataPath, schemaName = "bonus")

    application {
        val scope = rememberCoroutineScope { Dispatchers.Default }
        val configState = remember { AppConfigState(initialConfig) }

        // 导航状态
        var showWelcome by remember { mutableStateOf(true) }

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
                        databaseOp.close()
                        exitApplication()
                    }
                }
            )
        }

        Window(
            icon = Logo(),
            state = winState,
            title = "别奖励了! v${BuildConfig.VERSION}",
            visible = !winState.isMinimized,
            enabled = !winState.isMinimized,
            onCloseRequest = {
                exitApplication()
                databaseOp.close()
            }
        ) {
            // 窗口焦点恢复：从托盘恢复时置顶
            LaunchedEffect(winState.isMinimized) {
                if (!winState.isMinimized) {
                    window.toFront()
                    window.requestFocus()
                }
            }

            CompositionLocalProvider(LocalAppConfig provides configState) {
                StopBonusTheme {
                    if (showWelcome) {
                        WelcomeNavHost(
                            onEnterApp = { showWelcome = false }
                        )
                    } else {
                        App(
                            state = remember {
                                AppState(
                                    winState,
                                    scope,
                                    databaseOp
                                )
                            },
                            onBackToWelcome = { showWelcome = true }
                        )
                    }
                }
            }
        }

    }
}
