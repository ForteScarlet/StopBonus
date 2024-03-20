package view.account.record

import FontBTTFamily
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import database.entity.BonusRecord
import database.entity.BonusRecordWeapons.weapon
import database.entity.BonusRecords
import database.entity.Weapon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.mapLazy
import view.account.AccountViewPage
import view.account.PageViewState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId


/**
 *
 * @author ForteScarlet
 */
object AccountBonusRecordView : AccountViewPage {

    override val isMenuIconSupport: Boolean
        get() = true

    @Composable
    override fun menuIcon(state: PageViewState) {
        Icon(Icons.Filled.DateRange, "Record icon")
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

private data class RecordData(
    val id: Int,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Duration,
    val score: UInt,
    val weapons: List<Weapon>
)

@Composable
private fun ShowBonusRecordList(state: PageViewState) {
    val scope = rememberCoroutineScope()
    val recordList = remember { mutableStateListOf<RecordData>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        state.accountState.inAccountTransaction { account ->
            val all = account.records
                .orderBy(BonusRecords.createTime to SortOrder.DESC)
                .with(BonusRecord::weapons)
                .notForUpdate()
                .mapLazy {
                    RecordData(
                        id = it.id.value,
                        startTime = it.startTime,
                        endTime = it.endTime,
                        duration = it.duration,
                        score = it.score,
                        weapons = it.weapons.toList(),
                    )
                }

            recordList.addAll(all)
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Top)
    ) {
        items(recordList) { record ->
            ListItemRecord(state, scope, record, onDelete = { recordList.remove(it) })
        }
    }
}

@Composable
private fun ListItemRecord(
    state: PageViewState,
    scope: CoroutineScope,
    record: RecordData,
    onDelete: (RecordData) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    var deleteConfirm by remember { mutableStateOf(false) }
    if (deleteConfirm) {
        var onDeleting by remember(deleteConfirm) { mutableStateOf(false) }
        AlertDialog(
            icon = { Icon(Icons.Filled.Warning, "Warning") },
            title = { Text("删除这条奖励记录?", fontFamily = FontBTTFamily) },
            onDismissRequest = {
                if (!onDeleting) {
                    deleteConfirm = false
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleting = true
                    // do delete
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
                }) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = if (!onDeleting) {
                {
                    TextButton(onClick = { deleteConfirm = false }) { Text("取消") }
                }
            } else null
        )
    }

    ListItem(
        modifier = Modifier.hoverable(interactionSource),
        headlineContent = {
            if (record.weapons.isEmpty()) {
                Text("手艺活")
            } else {
                val weaponsString = record.weapons.joinToString("、", prefix = "「", postfix = "」") { it.name }
                Text("使用 $weaponsString")
            }
        },
        supportingContent = {
            val start = record.startTime.atZone(ZoneId.systemDefault())
            val end = record.endTime.atZone(ZoneId.systemDefault())
            val startDate = start.toLocalDate()
            val endDate = end.toLocalDate()
            val startTime = start.toLocalTime()
            val endTime = end.toLocalTime()

            if (startDate == endDate) {
                Text("从 $startDate 的 $startTime 开始, 直到 $endTime")
            } else {
                Text("从 $startDate 的 $startTime 开始, 直到 $endDate 的 $endTime")
            }
        },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(isHovered) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete icon",
                        modifier = Modifier
                            .clip(ButtonDefaults.shape)
                            .clickable(isHovered) {
                                deleteConfirm = true
                            }
                    )
                }
                Text("持续:" + record.duration.format())
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
