package view.welcome

import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import config.AppConfig
import config.ConfigManager
import config.LocalAppConfig
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileWithDefaultApplication
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_arrow_back
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_home
import org.jetbrains.compose.resources.painterResource
import storeAppPath
import update.UpdateCoordinator
import update.UpdateState
import view.common.StopBonusFilledTonalButton
import view.common.StopBonusTextButton
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/**
 * 配置页面
 *
 * 简洁现代的页面布局，从上往下排列，水平居中
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfigPage(
    onBack: () -> Unit,
    updates: UpdateCoordinator?,
    updateState: UpdateState?,
    onInstallUpdate: () -> Unit,
) {
    val configState = LocalAppConfig.current
    var expanded by remember { mutableStateOf(false) }
    var selectedTimezone by remember { mutableStateOf(configState.config.timezone) }
    var autoCheckUpdates by remember { mutableStateOf(configState.config.autoCheckUpdates) }
    val settingsEdited = selectedTimezone != configState.config.timezone ||
        autoCheckUpdates != configState.config.autoCheckUpdates
    val controlsLocked = updateState?.let { it.verifying || it.confirmation != null || it.installing || it.handedOff } == true
    val dataDir = remember { ConfigManager.appDataDir() }
    val storeDir = remember { storeAppPath().toAbsolutePath() }
    val logDir = remember { Path(".logs").toAbsolutePath() }
    var openDirError by remember { mutableStateOf<String?>(null) }
    var saveFailed by remember { mutableStateOf(false) }

    updateState?.confirmation?.let { version ->
        AlertDialog(
            onDismissRequest = { updates?.cancelConfirmation() },
            title = { Text("安装 ${version.label}") },
            text = { Text("更新后将重新启动 StopBonus。") },
            confirmButton = { TextButton(onClick = onInstallUpdate) { Text("安装更新") } },
            dismissButton = { TextButton(onClick = { updates?.cancelConfirmation() }) { Text("取消") } },
        )
    }

    openDirError?.let { message ->
        AlertDialog(
            onDismissRequest = { openDirError = null },
            title = { Text("打开目录失败", fontFamily = FontLXGWNeoXiHeiScreenFamily()) },
            text = { Text(message, fontFamily = FontLXGWNeoXiHeiScreenFamily()) },
            confirmButton = {
                StopBonusTextButton(onClick = { openDirError = null }) {
                    Text("确定", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部栏：返回按钮 + 标题
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !controlsLocked) {
                Icon(
                    painter = painterResource(Res.drawable.icon_arrow_back),
                    contentDescription = "返回"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                "配置",
                fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                fontSize = 20.sp
            )
        }

        HorizontalDivider()

        // 内容区域
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (updates != null && updateState != null) {
                UpdateSettings(updates, updateState, autoCheckUpdates, { autoCheckUpdates = it }, settingsEdited)
                HorizontalDivider()
            }

            // 配置存储目录（只读展示）
            OutlinedTextField(
                label = { Text("配置存储目录") },
                value = dataDir.toString(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        openDirectory(dataDir).onFailure { error ->
                            openDirError = directoryOpenError(dataDir, error)
                        }
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.icon_home),
                            contentDescription = "打开配置目录"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 数据存储目录（只读展示）
            OutlinedTextField(
                label = { Text("数据存储目录") },
                value = storeDir.toString(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        openDirectory(storeDir).onFailure { error ->
                            openDirError = directoryOpenError(storeDir, error)
                        }
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.icon_home),
                            contentDescription = "打开数据目录"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 日志目录（只读展示）
            OutlinedTextField(
                label = { Text("日志存储目录") },
                value = logDir.toString(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        openDirectory(logDir).onFailure { error ->
                            openDirError = directoryOpenError(logDir, error)
                        }
                    }) {
                        Icon(
                            painter = painterResource(Res.drawable.icon_home),
                            contentDescription = "打开日志目录"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 时区设置
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!controlsLocked) expanded = it }
                ) {
                    OutlinedTextField(
                        label = { Text("时区") },
                        value = AppConfig.getTimezoneDisplayName(selectedTimezone),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !controlsLocked)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        AppConfig.AVAILABLE_TIMEZONES.forEach { tz ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        tz.displayName,
                                        fontFamily = FontLXGWNeoXiHeiScreenFamily()
                                    )
                                },
                                onClick = {
                                    selectedTimezone = tz.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

        }

        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            if (saveFailed) {
                Text("保存配置失败，请检查配置目录权限后重试。", color = MaterialTheme.colorScheme.error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StopBonusTextButton(onClick = onBack, enabled = !controlsLocked) {
                    Text("取消", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                }

                Spacer(modifier = Modifier.width(8.dp))

                StopBonusFilledTonalButton(enabled = !controlsLocked, onClick = {
                    val config = configState.config.copy(timezone = selectedTimezone, autoCheckUpdates = autoCheckUpdates)
                    configState.updateConfig(config).onSuccess { onBack() }.onFailure { saveFailed = true }
                }) {
                    Text("保存", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                }
            }
        }
    }
}

private fun openDirectory(directory: Path): Result<Unit> = runCatching {
    directory.createDirectories()
    FileKit.openFileWithDefaultApplication(PlatformFile(directory.toString()))
}

private fun directoryOpenError(directory: Path, error: Throwable): String = buildString {
    appendLine(directory)
    append(error.message ?: error.toString())
}.trim()
