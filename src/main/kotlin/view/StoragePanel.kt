package view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import database.AmbiguousLegacyTime
import database.DatabaseOperator
import database.MigrationPreview
import database.MigrationStage
import database.OverlapResolution
import database.StorageManager
import database.StorageStatus
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileWithDefaultApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun StoragePanel(
    storage: StorageManager,
    autoOpen: Boolean,
    onOpen: (DatabaseOperator, Path?) -> Unit,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<StorageStatus?>(null) }
    var selected by remember { mutableStateOf<Path?>(null) }
    var restoreSource by remember { mutableStateOf<Path?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var zoneText by remember { mutableStateOf(ZoneId.systemDefault().id) }
    var overlap by remember { mutableStateOf(OverlapResolution.REQUIRE_CONFIRMATION) }
    var ambiguous by remember { mutableStateOf(false) }
    val stages = remember { MutableStateFlow<MigrationStage?>(null) }
    val stage by stages.collectAsState()
    var switching by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf<MigrationPreview?>(null) }

    suspend fun refresh(openActive: Boolean = false) {
        val result = withContext(Dispatchers.IO) { storage.inspect() }
        status = result
        if (result is StorageStatus.Legacy && selected == null) selected = result.sources.singleOrNull()
        if (result is StorageStatus.Active && openActive) {
            onOpen(withContext(Dispatchers.IO) { storage.open() }, result.changedLegacy)
        }
    }

    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        notice = null
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (failure is AmbiguousLegacyTime) ambiguous = true
                error = failure.message ?: "数据库操作失败。"
            } finally {
                stages.value = null
                switching = false
                busy = false
            }
        }
    }

    fun chooseLegacyFile() {
        runAction {
            val file = FileKit.openFilePicker(dialogSettings = FileKitDialogSettings(title = "选择旧 H2 数据库"))
            if (file != null) {
                selected = Path.of(file.absolutePath())
                ambiguous = false
                overlap = OverlapResolution.REQUIRE_CONFIRMATION
            }
        }
    }

    LaunchedEffect(Unit) {
        runAction { refresh(openActive = autoOpen) }
    }

    val progressTitle = if (switching) "正在启用 SQLite" else when (stage) {
        MigrationStage.BACKUP -> "正在备份旧数据库"
        MigrationStage.IMPORT -> "正在导入并核对记录"
        MigrationStage.VERIFY -> "正在检查数据库完整性"
        null -> null
    }

    val feedback: @Composable () -> Unit = {
        error?.let { StorageMessage(it, isError = true) }
        notice?.let { StorageMessage(it) }
    }

    StoragePage(pageKey = progressTitle ?: completed ?: status) {
        val finished = completed
        when {
            progressTitle != null -> {
                StorageHeading(progressTitle, "完成后会自动显示下一步。")
                LinearProgressIndicator(Modifier.fillMaxWidth(0.65f))
                Text(
                    "旧数据库会保留在原位置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            finished != null -> {
                StorageHeading("迁移完成", "StopBonus 已开始使用 SQLite 保存记录。")
                feedback()
                StorageCard {
                    MigrationCounts(finished)
                    StorageDetail("旧库备份", finished.backup.toString())
                }
                Button(enabled = !busy, onClick = { runAction { refresh(openActive = true) } }) {
                    Text("进入应用")
                }
            }
            else -> when (val current = status) {
                null -> {
                    if (error == null) {
                        StorageHeading("正在检查数据库")
                        CircularProgressIndicator()
                    } else {
                        StorageHeading("无法打开数据库", "可以重新检查，或选择已有的 SQLite 备份恢复。")
                        feedback()
                    }
                }
                is StorageStatus.Active -> {
                    StorageHeading("数据库备份与恢复", "将完整数据库保存到你选择的位置。")
                    feedback()
                    StorageCard { StorageDetail("数据目录", storage.directory.toString()) }
                    current.changedLegacy?.let { path ->
                        StorageMessage("检测到旧 H2 文件有变化。两份数据均已保留，请分别核对。\n$path")
                    }
                    StorageActions {
                        Button(enabled = !busy, onClick = {
                            runAction {
                                val destination = FileKit.openDirectoryPicker() ?: return@runAction
                                val backup = withContext(Dispatchers.IO) {
                                    storage.createBackup(Path.of(destination.absolutePath()))
                                }
                                notice = "备份已保存到 $backup"
                            }
                        }) { Text("创建 SQLite 备份") }
                        OutlinedButton(enabled = !busy, onClick = { runAction { refresh(openActive = true) } }) {
                            Text("进入应用")
                        }
                    }
                }
                is StorageStatus.Pending -> {
                    val preview = current.preview
                    StorageHeading("核对迁移结果", "导入和校验已完成。请核对以下汇总，再启用 SQLite 写入。")
                    feedback()
                    MigrationExplanation()
                    StorageCard {
                        MigrationCounts(preview)
                        val format = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss VV").withZone(preview.zone)
                        if (preview.earliest != null && preview.latest != null) {
                            StorageDetail("记录时间范围", "${format.format(preview.earliest)} 至 ${format.format(preview.latest)}")
                        }
                        StorageDetail("旧电脑系统时区", preview.zone.id)
                        StorageDetail("旧数据库", preview.source.toString())
                        StorageDetail("旧库备份", preview.backup.toString())
                    }
                    StorageMessage("启用后，新记录会保存到 SQLite。如果继续使用旧程序，两份数据库的记录将分别保存。")
                    StorageActions {
                        Button(enabled = !busy, onClick = {
                            runAction {
                                switching = true
                                withContext(Dispatchers.IO) { storage.activate() }
                                completed = preview
                            }
                        }) { Text("确认切换并启用写入") }
                        TextButton(enabled = !busy, onClick = {
                            runAction {
                                withContext(Dispatchers.IO) { storage.discardPreview() }
                                refresh()
                            }
                        }) { Text("保留预览并重新导入") }
                    }
                }
                else -> {
                    val candidates = (current as? StorageStatus.Legacy)?.sources.orEmpty()
                    val source = selected
                    val importing = candidates.isNotEmpty() || source != null
                    if (importing) {
                        StorageHeading("迁移旧版数据", "请先退出旧版应用（包括托盘）。我们会先备份旧库，校验通过后再请你确认切换。")
                    } else {
                        StorageHeading("设置数据库", "如果之前使用过 StopBonus，可以选择旧库文件导入。")
                    }
                    feedback()
                    if (importing) {
                        MigrationExplanation()
                        StorageCard {
                            if (candidates.isNotEmpty()) {
                                Text("旧数据库", style = MaterialTheme.typography.titleSmall)
                                Column(Modifier.selectableGroup()) {
                                    candidates.forEach { candidate ->
                                        StorageChoice(candidate.toString(), selected == candidate, !busy) {
                                            if (selected != candidate) {
                                                selected = candidate
                                                ambiguous = false
                                                overlap = OverlapResolution.REQUIRE_CONFIRMATION
                                            }
                                        }
                                    }
                                }
                            }
                            if (source != null) {
                                if (source !in candidates) StorageDetail("已选旧数据库", source.toString())
                                OutlinedTextField(
                                    modifier = Modifier.fillMaxWidth(),
                                    value = zoneText,
                                    onValueChange = {
                                        zoneText = it
                                        ambiguous = false
                                        overlap = OverlapResolution.REQUIRE_CONFIRMATION
                                    },
                                    enabled = !busy,
                                    singleLine = true,
                                    label = { Text("旧电脑系统时区") },
                                    supportingText = { Text("请填写保存旧记录时使用的系统时区，例如 Asia/Shanghai。") },
                                )
                                if (ambiguous) {
                                    Column(Modifier.selectableGroup()) {
                                        StorageChoice("使用回拨前的偏移", overlap == OverlapResolution.EARLIER, !busy) {
                                            overlap = OverlapResolution.EARLIER
                                        }
                                        StorageChoice("使用回拨后的偏移", overlap == OverlapResolution.LATER, !busy) {
                                            overlap = OverlapResolution.LATER
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        StorageCard { StorageDetail("数据目录", storage.directory.toString()) }
                    }
                    StorageActions {
                        if (source != null) {
                            Button(enabled = !busy, onClick = {
                                runAction {
                                    val zone = ZoneId.of(zoneText.trim())
                                    stages.value = MigrationStage.BACKUP
                                    withContext(Dispatchers.IO) {
                                        storage.stage(source, zone, overlap, onStage = { stages.value = it }) {
                                            coroutineContext.ensureActive()
                                        }
                                    }
                                    refresh()
                                }
                            }) { Text("备份并导入预览") }
                        } else if (current == StorageStatus.Empty) {
                            Button(enabled = !busy, onClick = {
                                runAction {
                                    withContext(Dispatchers.IO) { storage.createEmpty() }
                                    refresh(openActive = true)
                                }
                            }) { Text("新建数据库") }
                        }
                        OutlinedButton(enabled = !busy, onClick = ::chooseLegacyFile) { Text("选择旧库文件") }
                    }
                }
            }
        }

        if (busy && progressTitle == null && status != null) {
            CircularProgressIndicator()
        }
        StorageActions {
            if (!busy && completed == null) {
                if (status !is StorageStatus.Pending) {
                    TextButton(onClick = {
                        runAction {
                            val file = FileKit.openFilePicker(
                                dialogSettings = FileKitDialogSettings(title = "选择 StopBonus SQLite 备份"),
                            )
                            if (file != null) restoreSource = Path.of(file.absolutePath())
                        }
                    }) { Text("从 SQLite 备份恢复") }
                }
                TextButton(onClick = { runAction { refresh() } }) { Text("重新检查") }
            }
            if (!busy) {
                TextButton(onClick = {
                    runAction {
                        FileKit.openFileWithDefaultApplication(PlatformFile(storage.directory.toString()))
                    }
                }) { Text("打开数据目录") }
            }
            TextButton(onClick = onExit) { Text("退出") }
        }
    }

    restoreSource?.let { source ->
        AlertDialog(
            onDismissRequest = { restoreSource = null },
            title = { Text("恢复数据库") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("将使用所选备份恢复数据。应用会先把当前库保存在数据目录的 backups 文件夹。")
                    StorageDetail("所选备份", source.toString())
                }
            },
            confirmButton = {
                Button(onClick = {
                    restoreSource = null
                    runAction {
                        withContext(Dispatchers.IO) { storage.restore(source) }
                        refresh(openActive = true)
                    }
                }) { Text("确认恢复") }
            },
            dismissButton = { TextButton(onClick = { restoreSource = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun MigrationExplanation() {
    var expanded by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics { stateDescription = if (expanded) "已展开" else "已收起" },
        ) {
            Text(if (expanded) "收起迁移说明" else "为什么要迁移？")
        }
        if (expanded) {
            StorageCard {
                Text(
                    "新版 StopBonus 用 SQLite 保存本地数据。你可以在「数据库 → 备份与恢复」中创建完整备份，换电脑时也可以用这份备份恢复数据。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "从旧版升级时，需要导入一次 H2 中的账号、武器、奖励记录及其关联。应用会保留原库并核对导入结果，你确认切换后才会把新记录写入 SQLite。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StoragePage(pageKey: Any?, content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()
    LaunchedEffect(pageKey) { scrollState.scrollTo(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Box(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = viewportHeight).padding(horizontal = 24.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { content() }
            }
        }
    }
}

@Composable
private fun StorageHeading(title: String, description: String? = null) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (description != null) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StorageCard(
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = color,
        contentColor = contentColor,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun StorageMessage(message: String, isError: Boolean = false) {
    val color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor =
        if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    StorageCard(color, contentColor) {
        SelectionContainer(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StorageDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StorageChoice(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MigrationCounts(preview: MigrationPreview) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("账号" to preview.accounts, "武器" to preview.weapons, "奖励记录" to preview.records)
            .forEach { (label, count) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(count.toString(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }
    }
}

@Composable
private fun StorageActions(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}
