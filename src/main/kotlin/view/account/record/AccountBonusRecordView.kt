package view.account.record

import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import common.DateTimeFormatters
import config.LocalAppConfig
import database.entity.BonusRecord
import database.entity.BonusRecordView
import database.entity.BonusRecords
import database.entity.toView
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitDialogException
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_date_range
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_delete
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import view.account.PageViewState
import view.account.SimpleAccountViewPageSelector
import view.common.DeleteConfirmDialog
import view.common.StopBonusOutlinedButton
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit


/**
 *
 * @author ForteScarlet
 */
object AccountBonusRecordView : SimpleAccountViewPageSelector {

    override val isMenuIconSupport: Boolean
        get() = true

    @Composable
    override fun menuIcon(state: PageViewState) {
        Icon(painterResource(Res.drawable.icon_date_range), "Record icon")
    }

    @Composable
    override fun menuLabel(state: PageViewState) {
        Text("奖励记录")
    }

    @Composable
    override fun rightView(state: PageViewState) {
        ShowBonusRecordList(state)
    }
}

/*
var account by Account referencedOn BonusRecords.account
    var startTime by BonusRecords.startTime
    var endTime by BonusRecords.endTime
    var duration by BonusRecords.duration
    var score by BonusRecords.score

    var weapons by Weapon via BonusRecordWeapons
 */

// private data class RecordData(
//     val id: Int,
//     val startTime: Instant,
//     val endTime: Instant,
//     val duration: Duration,
//     val score: UInt,
//     val weapons: List<WeaponView>
// )

@Composable
private fun ShowBonusRecordList(state: PageViewState) {
    val scope = rememberCoroutineScope()
    val recordList = remember { mutableStateListOf<BonusRecordView>() }
    val listState = rememberLazyListState()
    val configState = LocalAppConfig.current
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var exportInProgress by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<PendingBonusRecordExport?>(null) }
    var exportedFile by remember { mutableStateOf<ExportedBonusRecordFile?>(null) }

    val fileSaverLauncher = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
        onError = { _: FileKitDialogException ->
            exportInProgress = false
            pendingExport = null
            showExportError(scope, state)
        },
        onResult = { file ->
            val export = pendingExport
            pendingExport = null
            if (file == null || export == null) {
                exportInProgress = false
            } else {
                scope.launch {
                    try {
                        file.write(export.bytes)
                        val absolutePath = file.absolutePath()
                        exportedFile = ExportedBonusRecordFile(file, absolutePath)
                    } catch (cause: CancellationException) {
                        throw cause
                    } catch (_: Exception) {
                        showExportError(scope, state)
                    } finally {
                        exportInProgress = false
                    }
                }
            }
        },
    )

    fun startExport(format: BonusRecordExportFormat) {
        exportMenuExpanded = false
        exportInProgress = true
        exportedFile = null
        scope.launch {
            try {
                val records = state.accountState.inAccountTransaction { account ->
                    BonusRecord.find { BonusRecords.account eq account.id }
                        .orderBy(
                            BonusRecords.startTime to SortOrder.ASC,
                            BonusRecords.id to SortOrder.ASC,
                        )
                        .with(BonusRecord::weapons)
                        .notForUpdate()
                        .map { it.toView() }
                }
                val bytes = BonusRecordExporter.bytes(format, records, configState.zoneId)
                pendingExport = PendingBonusRecordExport(bytes)
                fileSaverLauncher.launch(
                    suggestedName = "奖励记录_${LocalDate.now(configState.zoneId)}",
                    defaultExtension = format.extension,
                    allowedExtensions = setOf(format.extension),
                )
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Exception) {
                exportInProgress = false
                pendingExport = null
                showExportError(scope, state)
            }
        }
    }

    LaunchedEffect(Unit) {
        state.accountState.inAccountTransaction { account ->
            val all = BonusRecord.find { BonusRecords.account eq account.id }
                .orderBy(BonusRecords.createTime to SortOrder.DESC)
                .with(BonusRecord::weapons)
                .notForUpdate()
                .map { it.toView() }

            recordList.addAll(all)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box {
                    StopBonusOutlinedButton(
                        onClick = { exportMenuExpanded = true },
                        enabled = !exportInProgress,
                    ) {
                        Text(if (exportInProgress) "导出中..." else "导出")
                    }
                    DropdownMenu(
                        expanded = exportMenuExpanded,
                        onDismissRequest = { exportMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("导出 CSV") },
                            onClick = { startExport(BonusRecordExportFormat.CSV) },
                        )
                        DropdownMenuItem(
                            text = { Text("导出 Excel") },
                            onClick = { startExport(BonusRecordExportFormat.EXCEL) },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Top),
            ) {
                items(recordList) { record ->
                    ListItemRecord(state, scope, record, onDelete = { recordList.remove(it) })
                }
            }
        }

        exportedFile?.let { export ->
            ExportedBonusRecordSnackbar(
                export = export,
                onDismiss = { exportedFile = null },
                onOpenFile = {
                    ExportFileActions.openFile(export.file)
                        .onFailure { showActionError(scope, state, "打开文件失败") }
                },
                onOpenDirectory = {
                    ExportFileActions.openDirectory(export.file)
                        .onFailure { showActionError(scope, state, "打开目录失败") }
                },
            )
        }
    }
}

private data class PendingBonusRecordExport(
    val bytes: ByteArray,
)

private data class ExportedBonusRecordFile(
    val file: PlatformFile,
    val absolutePath: String,
)

@Composable
private fun BoxScope.ExportedBonusRecordSnackbar(
    export: ExportedBonusRecordFile,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenDirectory: () -> Unit,
) {
    Snackbar(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp),
        action = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpenFile) { Text("打开文件") }
                TextButton(onClick = onOpenDirectory) { Text("打开目录") }
            }
        },
        dismissAction = {
            IconButton(onClick = onDismiss) { Text("×") }
        },
    ) {
        Text("已导出：${export.absolutePath}")
    }
}

private fun showExportError(scope: CoroutineScope, state: PageViewState) {
    showActionError(scope, state, "导出失败，请检查文件权限后重试")
}

private fun showActionError(scope: CoroutineScope, state: PageViewState, message: String) {
    scope.launch {
        state.snackbarHostState.showSnackbar(message, withDismissAction = true)
    }
}

@Composable
private fun ListItemRecord(
    state: PageViewState,
    scope: CoroutineScope,
    record: BonusRecordView,
    onDelete: (BonusRecordView) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    var deleteConfirm by remember { mutableStateOf(false) }
    if (deleteConfirm) {
        var onDeleting by remember(deleteConfirm) { mutableStateOf(false) }
        DeleteConfirmDialog(
            title = "删除这条奖励记录?",
            isDeleting = onDeleting,
            onConfirm = {
                onDeleting = true
                scope.launch {
                    try {
                        state.accountState.database.inSuspendedTransaction {
                            BonusRecords.deleteWhere(limit = 1) { BonusRecords.id eq record.id }
                        }
                        scope.launch {
                            state.snackbarHostState.showSnackbar(
                                "奖励记录已删除",
                                withDismissAction = true
                            )
                        }
                        onDelete(record)
                    } finally {
                        deleteConfirm = false
                        onDeleting = false
                    }
                }
            },
            onDismiss = { deleteConfirm = false }
        )
    }

    ListItem(
        modifier = Modifier.hoverable(interactionSource),
        headlineContent = {
            if (record.weapons.isEmpty()) {
                Text("手艺活", fontFamily = FontLXGWNeoXiHeiScreenFamily())
            } else {
                val weaponsString = record.weapons.joinToString("、", prefix = "「", postfix = "」") { it.name }
                Text("使用 $weaponsString", fontFamily = FontLXGWNeoXiHeiScreenFamily())
            }
        },
        supportingContent = {
            val configState = LocalAppConfig.current
            val zoneId = configState.zoneId

            val start = record.startTime.atZone(zoneId)
            val end = record.endTime.atZone(zoneId)
            val startDate = start.toLocalDate()
            val endDate = end.toLocalDate()
            val startTime = start.toLocalTime()
            val endTime = end.toLocalTime()
            val remark = record.remark
            val score = record.score

            // 明确日期格式
            val startDateDisplay = DateTimeFormatters.formatDate(startDate)
            val startTimeDisplay = DateTimeFormatters.formatTime(startTime)
            val endTimeDisplay = DateTimeFormatters.formatTime(endTime)
            val daysDiff = ChronoUnit.DAYS.between(startDate, endDate)

            Column {
                val timeRangeText = when {
                    daysDiff == 0L -> "$startDateDisplay $startTimeDisplay 开始, 直到 $endTimeDisplay"
                    daysDiff == 1L -> "$startDateDisplay $startTimeDisplay 开始, 直到次日 $endTimeDisplay"
                    else -> {
                        val endDateDisplay = DateTimeFormatters.formatDate(endDate)
                        "$startDateDisplay $startTimeDisplay 开始, 直到 $endDateDisplay $endTimeDisplay"
                    }
                }
                Text(timeRangeText, fontFamily = FontLXGWNeoXiHeiScreenFamily())
                // 评分
                Row {
                    Text("评分: ", fontWeight = FontWeight.Bold, fontFamily = FontLXGWNeoXiHeiScreenFamily())
                    Text(score.toString(), fontFamily = FontLXGWNeoXiHeiScreenFamily())
                }

                // 备注
                Row {
                    Text("备注: ", fontWeight = FontWeight.Bold, fontFamily = FontLXGWNeoXiHeiScreenFamily())
                    Text(remark.ifBlank { "无" }, fontFamily = FontLXGWNeoXiHeiScreenFamily())
                }
            }

        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(isHovered) {
                    Icon(
                        painter = painterResource(Res.drawable.icon_delete),
                        contentDescription = "Delete icon",
                        modifier = Modifier
                            .clip(ButtonDefaults.shape)
                            .clickable(isHovered) {
                                deleteConfirm = true
                            }
                    )
                }
                Text("持续:" + record.duration.format(), fontFamily = FontLXGWNeoXiHeiScreenFamily())
            }
        }
    )
}

fun Duration.format(): String {
    toString()
    return buildString {
        with(toDaysPart()) {
            if (this > 0) append(this).append("天")
        }
        with(toHoursPart()) {
            if (this > 0) append(this).append("时")
        }
        append(toMinutesPart()).append("分")
    }
}
