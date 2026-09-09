package view.account.home

import FontBTTFamily
import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import common.DateTimeFormatters
import common.Emojis
import common.Limits
import config.LocalAppConfig
import database.entity.*
import kotlinx.coroutines.launch
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_clear
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_home
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import picker.core.*
import picker.desktop.InstantPickerPanel
import picker.foundation.rememberPickerNow
import picker.material.MaterialPickerTheme
import view.account.AccountViewPage
import view.account.AccountViewPageSelector
import view.account.PageViewState
import view.common.StopBonusElevatedButton
import view.common.StopBonusOutlinedButton
import view.common.StopBonusTextButton
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 *
 * @author ForteScarlet
 */
object AccountHomeView : AccountViewPageSelector {

    @Composable
    override fun navigationDrawerItem(
        state: PageViewState,
        selected: AccountViewPage?,
        shape: Shape,
        onSelect: (AccountViewPage?) -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavigationDrawerItem(
                modifier = Modifier.fillMaxWidth(.5f),
                selected = this == selected,
                shape = shape,
                onClick = {
                    onSelect(if (AccountHomePage == selected) null else AccountHomePage)
                },
                icon = {
                    menuIcon(state)
                },
                label = {
                    Text("我打了😢", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                }
            )

            // TODO
            NavigationDrawerItem(
                modifier = Modifier.clickable(false) {}.hoverable(remember { MutableInteractionSource() }, false),
                selected = false, // TODO
                shape = shape,
                onClick = {
                    // onSelect(if (thisPage == selected) null else thisPage)
                },
                icon = {
                    menuIcon(state)
                },
                label = {
                    Column {
                        Text("现在开打😡", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                        Text(
                            "(暂不可用)",
                            fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    }

    // override val isMenuIconSupport: Boolean
    //     get() = true
    //

    @Composable
    private fun menuIcon(@Suppress("UNUSED_PARAMETER") state: PageViewState) {
        Icon(painterResource(Res.drawable.icon_home), "Home icon")
    }

    private data object AccountHomePage : AccountViewPage {
        @Composable
        override fun rightView(state: PageViewState) {
            AccountHome(state)
        }
    }


}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountHome(state: PageViewState) {
    val configState = LocalAppConfig.current
    val environment = remember(configState.clock, configState.zoneId) {
        PickerEnvironment(
            clock = configState.clock,
            zoneId = configState.zoneId,
        )
    }
    val zoneId = environment.zoneId
    val now = rememberPickerNow(environment)

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var startInstant by remember { mutableStateOf<Instant?>(null) }
    var endInstant by remember { mutableStateOf<Instant?>(null) }
    var activeTimePicker by remember { mutableStateOf<RecordTimeField?>(null) }

    val latestDate = now.date.coerceAtLeast(EARLIEST_RECORD_DATE)
    val startDateConstraints = DateConstraints(
        min = EARLIEST_RECORD_DATE,
        max = latestDate,
    )
    val endDateConstraints = DateConstraints(
        min = EARLIEST_RECORD_DATE,
        max = latestDate,
    )
    val endOrderValidator = remember(startInstant) {
        ValueValidator<Instant> { candidate, _ ->
            if (startInstant != null && candidate <= startInstant) {
                ValidationResult.Invalid(PickerIssue(PickerIssueCodes.EndBeforeStart))
            } else {
                ValidationResult.Valid
            }
        }
    }
    val startInstantConstraints = InstantConstraints(max = now.instant)
    val endInstantConstraints = InstantConstraints(
        max = now.instant,
        additional = endOrderValidator,
    )
    val format = TimeFormatOptions(precision = TimePrecision.Second)

    var weapon by remember { mutableStateOf<WeaponView?>(null) }
    val score = remember { SliderState(value = 10f, steps = 8, valueRange = 1f..10f) }
    var remarkValue by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }

    fun clearStates() {
        startInstant = null
        endInstant = null
        activeTimePicker = null
        weapon = null
        score.value = 10f
        remarkValue = ""
    }

    fun displayInstant(value: Instant?): String {
        val local = value?.atZone(zoneId)?.toLocalDateTime() ?: return ""
        return DateTimeFormatters.formatDateTime(local, environment.locale)
        // return DateTimeFormatters.formatDate(local.toLocalDate(), environment.locale) + " " +
        //     "%02d:%02d:%02d".format(Locale.ROOT, local.hour, local.minute, local.second)
    }

    val duration = if (startInstant != null && endInstant != null) {
        Duration.between(startInstant, endInstant)
    } else {
        null
    }
    val recordTimes = validateRecordTimes(startInstant, endInstant, environment, now)
    val canRecord = recordTimes is RecordTimesResult.Valid

    MaterialPickerTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RecordTimePickerButton(
                label = "开始时间",
                value = startInstant,
                enabled = !recording,
                onOpen = { activeTimePicker = RecordTimeField.Start },
                onUseNow = {
                    startInstant = environment.sampleNow().instant.truncatedTo(ChronoUnit.SECONDS)
                    endInstant = null
                },
                display = ::displayInstant,
            )

            RecordTimePickerButton(
                label = "结束时间",
                value = endInstant,
                enabled = startInstant != null && !recording,
                onOpen = { activeTimePicker = RecordTimeField.End },
                onUseNow = { endInstant = environment.sampleNow().instant.truncatedTo(ChronoUnit.SECONDS) },
                display = ::displayInstant,
                supportingText = if (startInstant == null) "请先选择开始时间" else null,
            )
            if (recordTimes is RecordTimesResult.Invalid &&
                recordTimes.field == RecordTimeField.End &&
                endInstant != null
            ) {
                Text(
                    text = PickerStrings.forLocale(environment.locale).message(recordTimes.issue),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            AnimatedVisibility(canRecord) {
                WeaponSelector(
                    state = state,
                    selectedWeapon = weapon,
                    enabled = !recording,
                    onSelect = { weapon = it },
                )
            }
            AnimatedVisibility(canRecord) {
                ScoreSelector(score, enabled = !recording)
            }
            AnimatedVisibility(canRecord) {
                OutlinedTextField(
                    enabled = !recording,
                    value = remarkValue,
                    onValueChange = {
                        remarkValue =
                            if (it.length <= Limits.REMARK_MAX_LENGTH) {
                                it
                            } else {
                                it.substring(0, Limits.REMARK_MAX_LENGTH)
                            }
                    },
                    label = { Text("备注") },
                    placeholder = { Text("备注") },
                    supportingText = {
                        Text(
                            remarkValue.length.toString() + " / " +
                                Limits.REMARK_MAX_LENGTH.toString()
                        )
                    },
                )
            }

            AnimatedVisibility(startInstant != null && endInstant != null) {
                StopBonusElevatedButton(
                    enabled = !recording && canRecord,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                    onClick = {
                        val submitNow = environment.sampleNow()
                        val submittedStart = startInstant
                        val submittedEnd = endInstant
                        if (submittedStart != null && submittedEnd != null) {
                                when (
                                    val validated = validateRecordTimes(
                                        submittedStart,
                                        submittedEnd,
                                        environment,
                                        submitNow,
                                    )
                                ) {
                                    is RecordTimesResult.Invalid -> Unit

                                    is RecordTimesResult.Valid -> {
                                        val snapshotStart = validated.start
                                        val snapshotEnd = validated.end
                                        val snapshotDuration = validated.duration
                                        val snapshotScore = score.value.toUInt()
                                        val snapshotRemark = remarkValue
                                        val snapshotWeapon = weapon
                                        recording = true
                                        scope.launch {
                                            try {
                                                state.accountState.inAccountTransaction { account ->
                                                    BonusRecord.new {
                                                        this.account = Account.findById(account.id)
                                                            ?: error("当前账号不存在")
                                                        this.startTime = snapshotStart
                                                        this.endTime = snapshotEnd
                                                        this.duration = snapshotDuration
                                                        this.score = snapshotScore
                                                        this.remark = snapshotRemark
                                                        snapshotWeapon?.let { selected ->
                                                            Weapon.findById(selected.id)?.let { entity ->
                                                                this.weapons = SizedCollection(listOf(entity))
                                                            }
                                                        }
                                                    }
                                                }
                                                state.snackbarHostState.showSnackbar(
                                                    "记录已保存。你就打吧！",
                                                    withDismissAction = true,
                                                )
                                                clearStates()
                                            } catch (e: Exception) {
                                                state.snackbarHostState.showSnackbar(
                                                    "记录失败: " + (e.message ?: "未知错误"),
                                                    withDismissAction = true,
                                                )
                                            } finally {
                                                recording = false
                                            }
                                        }
                                    }
                                }
                            }
                    },
                ) {
                    if (duration != null) {
                        if (duration.isNegative) {
                            Text(
                                "时光回溯是吧！" + Emojis.ANGRY,
                                modifier = Modifier.align(Alignment.CenterVertically),
                                fontFamily = FontBTTFamily(),
                                fontSize = TextUnit(50f, TextUnitType.Sp),
                            )
                        } else if (duration.toMinutes() <= 0) {
                            Text(
                                "一分钟都没有？😰",
                                modifier = Modifier.align(Alignment.CenterVertically),
                                fontFamily = FontBTTFamily(),
                                fontSize = TextUnit(50f, TextUnitType.Sp),
                            )
                        } else {
                            Text(
                                "就打就打" + Emojis.ANGRY + Emojis.ANGRY + Emojis.ANGRY,
                                modifier = Modifier.align(Alignment.CenterVertically),
                                fontFamily = FontBTTFamily(),
                                fontSize = TextUnit(50f, TextUnitType.Sp),
                            )
                        }
                    }
                }
            }
        }
        activeTimePicker?.let { field ->
            RecordTimePickerDialog(
                field = field,
                value = if (field == RecordTimeField.Start) startInstant else endInstant,
                environment = environment,
                format = format,
                dateConstraints = if (field == RecordTimeField.Start) startDateConstraints else endDateConstraints,
                constraints = if (field == RecordTimeField.Start) startInstantConstraints else endInstantConstraints,
                onValueChange = { selected ->
                    if (field == RecordTimeField.Start) {
                        startInstant = selected
                        endInstant = null
                    } else {
                        endInstant = selected
                    }
                },
                onUseNow = {
                    val current = environment.sampleNow().instant.truncatedTo(ChronoUnit.SECONDS)
                    if (field == RecordTimeField.Start) {
                        startInstant = current
                        endInstant = null
                    } else {
                        endInstant = current
                    }
                },
                onClear = {
                    if (field == RecordTimeField.Start) {
                        startInstant = null
                        endInstant = null
                    } else {
                        endInstant = null
                    }
                },
                onDismiss = { activeTimePicker = null },
            )
        }
    }
}

/**
 * 业务页的时间选择入口。
 *
 * 它仅作为按钮触发器，不允许直接编辑文本；“现在”是业务页自行决定的快捷操作。
 */
@Composable
private fun RecordTimePickerButton(
    label: String,
    value: Instant?,
    enabled: Boolean,
    onOpen: () -> Unit,
    onUseNow: () -> Unit,
    display: (Instant?) -> String,
    supportingText: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, fontFamily = FontLXGWNeoXiHeiScreenFamily())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StopBonusOutlinedButton(
                onClick = onOpen,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = value?.let(display)?.ifEmpty { "选择时间" } ?: "选择时间",
                    fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                )
            }
            StopBonusTextButton(onClick = onUseNow, enabled = enabled) {
                Text("现在", fontFamily = FontLXGWNeoXiHeiScreenFamily())
            }
        }
        supportingText?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 业务页以对话框承载无容器的即时选择面板。
 *
 * 面板本身不内置确认、清空或“现在”动作；这些动作均由此业务容器提供，可按相同
 * 方式替换为 Popup、抽屉或独立窗口。
 */
@Composable
private fun RecordTimePickerDialog(
    field: RecordTimeField,
    value: Instant?,
    environment: PickerEnvironment,
    format: TimeFormatOptions,
    dateConstraints: DateConstraints,
    constraints: InstantConstraints,
    onValueChange: (Instant) -> Unit,
    onUseNow: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(visible = appeared, enter = fadeIn() + scaleIn(initialScale = 0.94f)) {
            Surface(
                modifier = Modifier.widthIn(min = 680.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AnimatedContent(
                        targetState = value,
                        label = "已选时间过渡",
                    ) { selected ->
                        Text(
                            text = (if (field == RecordTimeField.Start) "选择开始时间" else "选择结束时间"),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    InstantPickerPanel(
                        value = value,
                        onValueChange = onValueChange,
                        environment = environment,
                        format = format,
                        dateConstraints = dateConstraints,
                        constraints = constraints,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        StopBonusTextButton(
                            onClick = {
                                onClear()
                                // 清空是提交到业务状态的终止动作；关闭对话框能立即反馈字段已无值。
                                onDismiss()
                            },
                        ) { Text("清空") }
                        StopBonusTextButton(onClick = onUseNow) { Text("现在") }
                        StopBonusElevatedButton(onClick = onDismiss) { Text("完成") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private inline fun WeaponSelector(
    state: PageViewState, selectedWeapon: WeaponView?,
    enabled: Boolean = true,
    crossinline onSelect: (WeaponView?) -> Unit
) {
    val weapons = remember { mutableStateListOf<WeaponView>() }
    LaunchedEffect(state) {
        val all = state.accountState.inAccountTransaction { account ->
            Weapon.find { Weapons.account eq account.id }
                .notForUpdate().map { it.toView() }
        }

        weapons.addAll(all)
    }

    var expanded by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }

    ExposedDropdownMenuBox(
        expanded = expanded,
        modifier = Modifier,
        onExpandedChange = {
            if (enabled) expanded = it
        },
    ) {
        OutlinedTextField(
            modifier = Modifier
                .focusable(false)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(.65f),
            enabled = enabled,
            value = if (!expanded) selectedWeapon?.name ?: "无" else value,
            onValueChange = {
                value = it
                expanded = true
            },
            textStyle = LocalTextStyle.current.copy(
                textAlign = TextAlign.Center
            ),
            placeholder = { Text("选择武器") },
            label = { Text("用的什么武器${Emojis.ANGRY}") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )

        // see https://stackoverflow.com/questions/76039608/editable-dynamic-exposeddropdownmenubox-in-jetpack-compose
        val currentSearchValue = value
        val filteredList =
            weapons.filter { currentSearchValue.isEmpty() || it.name.contains(currentSearchValue, true) }

        DropdownMenu(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .exposedDropdownSize(true),
            properties = PopupProperties(focusable = false),
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("无") },
                trailingIcon = { Icon(painterResource(Res.drawable.icon_clear), "Clear icon") },
                enabled = enabled,
                onClick = {
                    onSelect(null)
                    value = ""
                    expanded = false
                },
            )

            for (weapon in filteredList) {
                DropdownMenuItem(
                    text = { Text(weapon.name) },
                    enabled = enabled,
                    onClick = {
                        onSelect(weapon)
                        value = ""
                        expanded = false
                    },
                )
            }

        }
    }

}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreSelector(
    scoreState: SliderState,
    enabled: Boolean = true,
) {
    val scoreValue = scoreState.value.toInt()
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "体验分数",
                fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                fontSize = TextUnit(25f, TextUnitType.Sp)
            )
            val scoreValueEmoji = when (scoreValue) {
                in 1..2 -> "😰"
                in 3..4 -> "😓"
                5 -> "😐"
                in 6..8 -> "😍"
                else -> "🥵"
            }

            Crossfade(scoreValueEmoji) { ej ->
                Text(
                    ej,
                    fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                    fontSize = TextUnit(35f, TextUnitType.Sp)
                )
            }
            Text(
                ": $scoreValue", fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                fontSize = TextUnit(25f, TextUnitType.Sp)
            )
        }

        Slider(state = scoreState, enabled = enabled)
    }


}


// private fun Duration.isPositive(): Boolean = (seconds.toInt() or toNanosPart()) > 0
// (seconds | nanos) > 0
