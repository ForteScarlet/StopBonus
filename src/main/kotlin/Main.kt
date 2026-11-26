@file:Suppress("FunctionName")

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import config.*
import database.DatabaseOperator
import database.LEGACY_FILE_NAME
import database.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import love.forte.bonus.bonus_self_desktop.generated.resources.BTT
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.angry_face_with_horns
import org.jetbrains.compose.resources.painterResource
import org.slf4j.LoggerFactory
import view.App
import view.AppState
import view.StoragePanel
import view.common.StopBonusTheme
import view.welcome.WelcomeNavHost
import java.awt.Toolkit
import java.nio.file.Files
import java.nio.file.Path
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

private fun legacyDatabasePaths(): List<Path> = buildList {
    if (System.getenv("DEBUG").toBoolean() || System.getProperty("debug").toBoolean()) return@buildList
    add(Path("data", LEGACY_FILE_NAME))
    System.getenv("LOCALAPPDATA")?.let { add(Path(it, DATA_DIR_NAME, "data", LEGACY_FILE_NAME)) }
    val home = System.getProperty("user.home") ?: return@buildList
    add(Path(home, DATA_DIR_NAME, "data", LEGACY_FILE_NAME))
    if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
        val packages = Path(home, "AppData", "Local", "Packages")
        if (Files.isDirectory(packages)) {
            runCatching {
                Files.newDirectoryStream(packages) { packagePath ->
                    packagePath.fileName.toString().contains("stopbonus", ignoreCase = true)
                }.use { entries ->
                    entries.map { it.resolve("LocalCache/Local/$DATA_DIR_NAME/data/$LEGACY_FILE_NAME") }
                }
            }.onSuccess { addAll(it) }.onFailure { logger.warn("无法检查 MSIX 的旧版数据目录", it) }
        }
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

fun main() {
    // 设置全局未捕获异常处理器
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        logger.error("UncaughtExceptionHandler on Thread[{}]", t, e)
    }

    // 加载应用配置
    val initialConfig = ConfigManager.load()

    // 初始化全局 Clock 提供者
    ClockProvider.initialize(initialConfig.zoneId())

    val storage = StorageManager(storeAppPath(), legacyDatabasePaths())
    var openDatabase: DatabaseOperator? = null

    try {
        application {
            val scope = rememberCoroutineScope()
            var database by remember { mutableStateOf<DatabaseOperator?>(null) }
            var autoOpenStorage by remember { mutableStateOf(true) }
            var changedLegacy by remember { mutableStateOf<Path?>(null) }
            var closingDatabase by remember { mutableStateOf(false) }
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
                onCloseRequest = ::exitApplication
            ) {
                // 窗口焦点恢复：从托盘恢复时置顶
                LaunchedEffect(winState.isMinimized) {
                    if (!winState.isMinimized) {
                        window.toFront()
                        window.requestFocus()
                    }
                }

                val currentDatabase = database
                if (currentDatabase != null) {
                    MenuBar {
                        Menu("数据库") {
                            Item("备份与恢复", enabled = !closingDatabase) {
                                closingDatabase = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { currentDatabase.close() }
                                    } finally {
                                        autoOpenStorage = false
                                        database = null
                                        openDatabase = null
                                        closingDatabase = false
                                    }
                                }
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalAppConfig provides configState) {
                    StopBonusTheme {
                        changedLegacy?.let { path ->
                            AlertDialog(
                                onDismissRequest = { changedLegacy = null },
                                title = { Text("检测到旧数据库变化") },
                                text = { Text("StopBonus 继续使用 SQLite。请分别核对两份数据，旧库仍在以下位置。\n$path") },
                                confirmButton = { TextButton(onClick = { changedLegacy = null }) { Text("知道了") } },
                            )
                        }
                        if (closingDatabase) {
                            Text("正在关闭数据库…")
                        } else if (currentDatabase == null) {
                            StoragePanel(
                                storage = storage,
                                autoOpen = autoOpenStorage,
                                onOpen = { operator, changed ->
                                    openDatabase = operator
                                    database = operator
                                    changedLegacy = changed
                                },
                                onExit = ::exitApplication,
                            )
                        } else if (showWelcome) {
                            WelcomeNavHost(
                                onEnterApp = { showWelcome = false }
                            )
                        } else {
                            val appScope = rememberCoroutineScope()
                            App(
                                state = remember {
                                    AppState(
                                        winState,
                                        appScope,
                                        currentDatabase
                                    )
                                },
                                onBackToWelcome = { showWelcome = true }
                            )
                        }
                    }
                }
            }

        }
    } finally {
        try {
            openDatabase?.close()
        } finally {
            storage.close()
        }
    }
}
