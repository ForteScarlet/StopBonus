package view.account.record

import FontBTTFamily
import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import database.entity.BonusRecord
import database.entity.BonusRecordView
import database.entity.BonusRecords
import database.entity.toView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_date_range
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_delete
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import view.account.PageViewState
import view.account.SimpleAccountViewPageSelector
import view.common.DeleteConfirmDialog
import java.time.Duration
import java.time.ZoneId


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
            val start = record.startTime.atZone(ZoneId.systemDefault())
            val end = record.endTime.atZone(ZoneId.systemDefault())
            val startDate = start.toLocalDate()
            val endDate = end.toLocalDate()
            val startTime = start.toLocalTime()
            val endTime = end.toLocalTime()
            val remark = record.remark
            val score = record.score

            Column {
                if (startDate == endDate) {
                    Text("从 $startDate 的 $startTime 开始, 直到 $endTime", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                } else {
                    Text(
                        "从 $startDate 的 $startTime 开始, 直到 $endDate 的 $endTime",
                        fontFamily = FontLXGWNeoXiHeiScreenFamily()
                    )
                }
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
