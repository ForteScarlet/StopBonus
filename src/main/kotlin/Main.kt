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
import desktop.InstanceStart
import desktop.SingleInstance
import desktop.WindowsDesktop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import love.forte.bonus.bonus_self_desktop.generated.resources.BTT
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.angry_face_with_horns
import org.jetbrains.compose.resources.painterResource
import org.slf4j.LoggerFactory
import update.UpdateCoordinator
import view.App
import view.AppState
import view.StoragePanel
import view.common.StopBonusTheme
import view.welcome.WelcomeNavHost
import java.awt.Desktop
import java.awt.Dialog
import java.awt.EventQueue
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.desktop.AppReopenedListener
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.system.exitProcess
import javax.swing.JOptionPane


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
    if (debugMode()) {
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
    if (debugMode()) return@buildList
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

private fun debugMode() = System.getenv("DEBUG").toBoolean() || System.getProperty("debug").toBoolean()

private fun runtimeDirectory(): Path {
    val parent = if (debugMode()) {
        Files.createDirectories(storeAppPath()).toRealPath()
    } else {
        Path(requireNotNull(System.getProperty("user.home"))).toRealPath()
    }
    return parent.resolve(".stopbonus-runtime")
}

private fun showStartupError(message: String) {
    if (GraphicsEnvironment.isHeadless()) {
        System.err.println(message)
    } else {
        EventQueue.invokeAndWait {
            JOptionPane.showMessageDialog(null, message, "StopBonus", JOptionPane.WARNING_MESSAGE)
        }
    }
}

fun main() {
    // 设置全局未捕获异常处理器
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        logger.error("UncaughtExceptionHandler on Thread[{}]", t, e)
    }

    val start = try {
        SingleInstance.start(runtimeDirectory())
    } catch (failure: Exception) {
        logger.error("Could not start StopBonus", failure)
        showStartupError("无法启动 StopBonus。请检查当前用户的运行目录权限。")
        return
    } catch (failure: LinkageError) {
        logger.error("Could not initialize the desktop integration", failure)
        showStartupError("无法加载 StopBonus 的系统组件。请检查安装是否完整。")
        return
    }
    when (start) {
        is InstanceStart.Primary -> {
            start.instance.use(::runPrimary)
            exitProcess(0)
        }
        InstanceStart.Activated -> Unit
        InstanceStart.OtherSession -> showStartupError("StopBonus 已在另一个登录会话运行。请切换到原会话打开窗口。")
        InstanceStart.Unreachable -> showStartupError("StopBonus 已在运行，暂时无法唤醒。")
    }
}

private fun runPrimary(instance: SingleInstance) {
    // 加载应用配置
    val initialConfig = ConfigManager.load()

    // 初始化全局 Clock 提供者
    ClockProvider.initialize(initialConfig.zoneId())

    val storage = StorageManager(storeAppPath(), legacyDatabasePaths())
    var openDatabase: DatabaseOperator? = null

    try {
        application(exitProcessOnExit = false) {
            val scope = rememberCoroutineScope()
            val updates = remember { UpdateCoordinator(scope, debugMode()) }
            val updateState by updates.state.collectAsState()
            val quit = {
                if (!updates.state.value.installing) {
                    instance.closing = true
                    exitApplication()
                }
            }
            DisposableEffect(instance) {
                val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
                val listener = AppReopenedListener { instance.requestActivation() }
                val canReopen = desktop?.isSupported(Desktop.Action.APP_EVENT_REOPENED) == true
                if (canReopen) desktop.addAppEventListener(listener)
                onDispose {
                    if (canReopen) desktop.removeAppEventListener(listener)
                }
            }
            var database by remember { mutableStateOf<DatabaseOperator?>(null) }
            var autoOpenStorage by remember { mutableStateOf(true) }
            var changedLegacy by remember { mutableStateOf<Path?>(null) }
            var closingDatabase by remember { mutableStateOf(false) }
            var updateRecoveryError by remember { mutableStateOf(false) }
            val configState = remember { AppConfigState(initialConfig) }
            LaunchedEffect(configState.config.autoCheckUpdates) {
                updates.checkAutomatically(configState.config.autoCheckUpdates)
            }

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
                    onAction = { instance.requestActivation() },
                    menu = {
                        Item("Open") {
                            instance.requestActivation()
                        }
                        Separator()
                        Item("Exit") {
                            quit()
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
                onCloseRequest = quit
            ) {
                LaunchedEffect(window) {
                    for (ignored in instance.activations) {
                        if (instance.closing) continue
                        winState.isMinimized = false
                        withTimeoutOrNull(2_000) {
                            while (!window.isShowing || window.extendedState and Frame.ICONIFIED != 0) delay(16)
                            val target = window.ownedWindows
                                .filterIsInstance<Dialog>()
                                .lastOrNull { it.isModal && it.isShowing } ?: window
                            target.toFront()
                            target.requestFocus()
                            WindowsDesktop.bringToFront(target, window)
                            if (Desktop.isDesktopSupported()) {
                                val desktop = Desktop.getDesktop()
                                if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                                    desktop.requestForeground(true)
                                }
                            }
                        }
                    }
                }

                val currentDatabase = database
                val installUpdate = {
                    if (currentDatabase == null || closingDatabase) {
                        updates.installFailed("数据库正在切换，请稍后再试。")
                    } else if (updates.beginInstall()) {
                        if (WindowsDesktop.isWindows) instance.closing = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { currentDatabase.close() }
                                updates.installPreparedUpdate()
                                database = null
                                openDatabase = null
                                if (WindowsDesktop.isWindows) quit()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                logger.warn("Could not hand off to the update program", failure)
                                val reopened = try {
                                    withContext(Dispatchers.IO) { storage.open() }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (recoveryFailure: Exception) {
                                    logger.error("Could not reopen the database after the update failed", recoveryFailure)
                                    updateRecoveryError = true
                                    autoOpenStorage = false
                                    null
                                }
                                database = reopened
                                openDatabase = reopened
                                instance.closing = false
                                updates.installFailed("无法启动更新程序，请稍后重试。")
                            }
                        }
                    }
                    Unit
                }
                if (currentDatabase != null) {
                    MenuBar {
                        Menu("数据库") {
                            Item("备份与恢复", enabled = !closingDatabase && !updateState.installing) {
                                if (closingDatabase || updates.state.value.installing) return@Item
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
                        if (updateRecoveryError) {
                            AlertDialog(
                                onDismissRequest = { updateRecoveryError = false },
                                title = { Text("无法重新打开数据库") },
                                text = { Text("更新未启动。请在数据库页面重新检查并打开数据。") },
                                confirmButton = { TextButton(onClick = { updateRecoveryError = false }) { Text("确定") } },
                            )
                        }
                        if (updateState.installing) {
                            AlertDialog(
                                onDismissRequest = {},
                                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                                title = { Text("正在准备更新…") },
                                text = { Text("StopBonus 正在关闭数据库。") },
                                confirmButton = {},
                            )
                        }
                        changedLegacy?.let { path ->
                            AlertDialog(
                                onDismissRequest = { changedLegacy = null },
                                title = { Text("检测到旧数据库变化") },
                                text = { Text("StopBonus 继续使用 SQLite。请分别核对两份数据，旧库仍在以下位置。\n$path") },
                                confirmButton = { TextButton(onClick = { changedLegacy = null }) { Text("知道了") } },
                            )
                        }
                        if (updateState.handedOff) {
                            AlertDialog(
                                onDismissRequest = quit,
                                properties = DialogProperties(dismissOnClickOutside = false),
                                title = { Text("已交给更新程序") },
                                text = { Text("请等待更新程序完成。如果更新窗口已关闭且 StopBonus 仍在运行，可以退出后重新打开。") },
                                confirmButton = { TextButton(onClick = quit) { Text("退出 StopBonus") } },
                            )
                        } else if (closingDatabase) {
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
                                onExit = quit,
                            )
                        } else if (showWelcome) {
                            WelcomeNavHost(
                                onEnterApp = { showWelcome = false },
                                updates = updates,
                                onInstallUpdate = installUpdate,
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
