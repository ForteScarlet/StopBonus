package view.welcome

import FontLXGWNeoXiHeiScreenFamily
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
fun ConfigPage(onBack: () -> Unit) {
    val configState = LocalAppConfig.current
    var expanded by remember { mutableStateOf(false) }
    var selectedTimezone by remember { mutableStateOf(configState.config.timezone) }
    val dataDir = remember { ConfigManager.appDataDir() }
    val storeDir = remember { storeAppPath().toAbsolutePath() }
    val logDir = remember { Path(".logs").toAbsolutePath() }
    var openDirError by remember { mutableStateOf<String?>(null) }

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
            IconButton(onClick = onBack) {
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

        Spacer(modifier = Modifier.height(48.dp))

        // 内容区域
        Column(
            modifier = Modifier.widthIn(max = 400.dp).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
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
                    onExpandedChange = { expanded = it }
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
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
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

            Spacer(modifier = Modifier.height(16.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StopBonusTextButton(onClick = onBack) {
                    Text("取消", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                }

                Spacer(modifier = Modifier.width(8.dp))

                StopBonusFilledTonalButton(onClick = {
                    configState.updateTimezone(selectedTimezone)
                    onBack()
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
