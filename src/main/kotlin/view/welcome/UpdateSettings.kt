package view.welcome

import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import config.BuildConfig
import config.LocalAppConfig
import update.UpdateCoordinator
import update.UpdateState
import java.time.format.DateTimeFormatter

@Composable
internal fun VersionLabel(
    label: String,
    fontSize: TextUnit,
    hasUpdate: Boolean,
    onShowUpdate: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            fontFamily = FontLXGWNeoXiHeiScreenFamily(),
            fontSize = fontSize,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasUpdate) {
            val shape = RoundedCornerShape(6.dp)
            Text(
                "NEW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.clip(shape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .then(if (onShowUpdate == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onShowUpdate))
                    .semantics { contentDescription = "有新版本" }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
internal fun UpdateSettings(
    updates: UpdateCoordinator,
    state: UpdateState,
    autoCheck: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    settingsEdited: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    val zone = LocalAppConfig.current.zoneId
    val formatter = remember(zone) { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone) }
    var downloadError by remember { mutableStateOf(false) }
    val canInstall = updates.canInstall

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("应用更新", style = MaterialTheme.typography.titleMedium)
        VersionLabel(state.current.label, MaterialTheme.typography.bodyMedium.fontSize, state.newer != null)
        Text(
            when {
                state.handedOff -> "已交给更新程序"
                state.installing -> "正在准备更新…"
                state.checking -> "正在检查更新…"
                state.checkError != null -> "检查失败"
                state.newer != null -> "发现新版本 ${state.newer.label}"
                state.checkedAt != null -> "已是最新版本"
                else -> "尚未检查更新"
            }
        )
        if (state.checkError != null && state.newer != null) {
            Text("上次发现的新版本 ${state.newer.label}", style = MaterialTheme.typography.bodySmall)
        }
        state.checkedAt?.let {
            Text("上次成功检查 ${formatter.format(it)}", style = MaterialTheme.typography.bodySmall)
        }
        state.checkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (downloadError) {
            Text("无法打开下载页。你可以在浏览器中打开 ${BuildConfig.DOWNLOAD_URL}。", color = MaterialTheme.colorScheme.error)
        }
        val locked = state.installing || state.handedOff || state.verifying || state.confirmation != null
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !locked && !state.checking, onClick = updates::check) {
                Text("检查更新")
            }
            if (state.newer != null) {
                Button(enabled = !locked && !state.checking && (!canInstall || !settingsEdited), onClick = {
                    if (canInstall) {
                        updates.requestInstall()
                    } else {
                        downloadError = runCatching { uriHandler.openUri(BuildConfig.DOWNLOAD_URL) }.isFailure
                    }
                }) {
                    Text(if (canInstall) "立即更新" else "前往下载页")
                }
            }
        }
        if (state.newer != null) {
            val hint = when {
                canInstall && settingsEdited -> "请先保存或取消配置修改，再安装更新。"
                canInstall -> null
                System.getProperty("os.name").startsWith("Linux", ignoreCase = true) ->
                    "通过系统包管理器安装的版本请用原渠道更新。便携版可前往下载页。"
                else -> "当前构建可检查版本，请前往下载页手动更新。"
            }
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("启动时自动检查更新")
                Text(
                    if (updates.debug) "调试运行可手动检查更新。" else "发现新版本后会提示，安装前需要确认。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = autoCheck,
                onCheckedChange = onAutoCheckChange,
                enabled = !locked,
                modifier = Modifier.semantics { contentDescription = "启动时自动检查更新" },
            )
        }
    }
}
