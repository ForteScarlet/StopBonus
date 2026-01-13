package view.account.home

import FontBTTFamily
import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import org.jetbrains.exposed.sql.SizedCollection
import view.account.AccountViewPage
import view.account.AccountViewPageSelector
import view.account.PageViewState
import view.common.StopBonusButtonDefaults
import view.common.StopBonusElevatedButton
import view.common.StopBonusTextButton
import java.time.*

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
    val clock = configState.clock
    val zoneId = configState.zoneId

    val nowInstant = Instant.now(clock)
    val nowMillis = nowInstant.toEpochMilli()
    val nowLocalDateTime = LocalDateTime.now(clock)

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val nowYear = Year.now(clock)

    val startDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = null,
        initialDisplayedMonthMillis = nowMillis,
        yearRange = 1900..nowYear.value,
        initialDisplayMode = DisplayMode.Input,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Instant.ofEpochMilli(utcTimeMillis).atZone(zoneId)
                    .toLocalDate() <= nowLocalDateTime.toLocalDate()
        }
    )

    val selectedStartDateMillis = startDatePickerState.selectedDateMillis
    val selectedStartDateInstant = selectedStartDateMillis?.let { Instant.ofEpochMilli(it) }

    var startTimePickerValue by remember { mutableStateOf<LocalTime?>(null) }

    fun selectedStartDateTime(): LocalDateTime? {
        val selectedStartDateInstantValue = selectedStartDateInstant
            ?: return null
        val timeValue = startTimePickerValue
            ?: return null

        return selectedStartDateInstantValue.atZone(zoneId)
            .toLocalDate()
            .atTime(timeValue)
    }

    val selectedStartDateTime = selectedStartDateTime()


    val endDatePickerState = rememberDatePickerState(
        initialSelectedDateMillis = null,
        initialDisplayedMonthMillis = selectedStartDateMillis ?: nowMillis,
        yearRange = selectedStartDateTime?.let {
            it.year..nowYear.value
        } ?: 1900..nowYear.value,
        initialDisplayMode = DisplayMode.Input,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(zoneId).toLocalDate()
                val notInFuture = date <= nowLocalDateTime.toLocalDate()
                val afterStartDate = selectedStartDateTime?.toLocalDate()?.let { date >= it } ?: true
                return notInFuture && afterStartDate
            }
        }
    )

    val selectedEndDateMillis = endDatePickerState.selectedDateMillis
    val selectedEndDateInstant = selectedEndDateMillis?.let { Instant.ofEpochMilli(it) }
    // val selectedEndDateTime = selectedEndDateInstant?.atZone(ZoneId.systemDefault())
    //     ?.toLocalDate()
    //     ?.atTime(endTimePickerState.hour, endTimePickerState.minute)

    var endTimePickerValue by remember { mutableStateOf<LocalTime?>(null) }

    fun selectedEndDateTime(): LocalDateTime? {
        val selectedEndDateInstantValue = selectedEndDateInstant
            ?: return null
        val timeValue = endTimePickerValue
            ?: return null

        return selectedEndDateInstantValue.atZone(zoneId)
            .toLocalDate()
            .atTime(timeValue)
    }

    val selectedEndDateTime = selectedEndDateTime()

    // val endTimePickerState = rememberTimePickerState(
    //     is24Hour = true,
    //     initialHour = nowTime.hour,
    //     initialMinute = nowTime.minute
    // )

    var weapon by remember { mutableStateOf<WeaponView?>(null) }
    val score = remember { SliderState(value = 10f, steps = 8, valueRange = 1f..10f) }
    var remarkValue by remember { mutableStateOf("") }

    fun clearStates() {
        startDatePickerState.selectedDateMillis = null
        startTimePickerValue = null
        endDatePickerState.selectedDateMillis = null
        endTimePickerValue = null
        weapon = null
        score.value = 10f
        remarkValue = ""
    }

    Column(
        modifier = Modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // start date
        var showSelectStartDate by remember { mutableStateOf(false) }
        if (showSelectStartDate) {
            val nowTime0 = nowLocalDateTime
            val startTimePickerState = rememberTimePickerState(
                is24Hour = true,
                initialHour = nowTime0.hour,
                initialMinute = nowTime0.minute
            )

            DatePickerDialog(
                onDismissRequest = { showSelectStartDate = false },
                dismissButton = {
                    StopBonusTextButton(onClick = {
                        startDatePickerState.selectedDateMillis = nowMillis
                        startTimePickerValue = nowLocalDateTime.toLocalTime()
                        showSelectStartDate = false
                    }) {
                        Text(
                            "现在${Emojis.CLOCK}",
                            fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                        )
                    }
                },
                confirmButton = {
                    StopBonusTextButton(onClick = {
                        startTimePickerValue = LocalTime.of(startTimePickerState.hour, startTimePickerState.minute)
                        showSelectStartDate = false
                    }) {
                        Text(
                            "就是这时${Emojis.ANGRY}",
                            fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                        )
                    }
                },
            ) {
                Column {
                    DatePicker(
                        state = startDatePickerState,
                        title = {
                            Text(
                                "什么时候开始打的${Emojis.ANGRY}",
                                fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                                modifier = Modifier.padding(PaddingValues(start = 24.dp, end = 12.dp, top = 16.dp))
                            )
                        },
                    )

                    TimeInput(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp),
                        state = startTimePickerState,
                    )
                }
            }

        }

        StopBonusTextButton(
            modifier = Modifier.fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            onClick = { showSelectStartDate = true }
        ) {
            Text(
                "什么时候开始打的${Emojis.ANGRY}${Emojis.ANGRY}",
                fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                fontSize = TextUnit(50f, TextUnitType.Sp),
                modifier = Modifier
            )
        }

        AnimatedVisibility(visible = selectedStartDateTime != null) {
            Text(
                text = selectedStartDateTime?.let { DateTimeFormatters.formatDateTime(it) } ?: "",
                fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                fontSize = TextUnit(15f, TextUnitType.Sp),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
            )
        }


        // end date & time
        var showEndDateTime by remember { mutableStateOf(false) }
        if (showEndDateTime) {
            val nowTime0 = nowLocalDateTime
            val endTimePickerState = rememberTimePickerState(
                is24Hour = true,
                initialHour = nowTime0.hour,
                initialMinute = nowTime0.minute
            )

            DatePickerDialog(
                onDismissRequest = { showEndDateTime = false },
                dismissButton = {
                    StopBonusTextButton(onClick = {
                        endDatePickerState.selectedDateMillis = nowMillis
                        endTimePickerValue = nowLocalDateTime.toLocalTime()
                        showEndDateTime = false
                    }) {
                        Text(
                            "现在${Emojis.CLOCK}",
                            fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                        )
                    }
                },
                confirmButton = {
                    StopBonusTextButton(onClick = {
                        endTimePickerValue = LocalTime.of(endTimePickerState.hour, endTimePickerState.minute)
                        showEndDateTime = false
                    }) {
                        Text(
                            "就是这时${Emojis.ANGRY}",
                            fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                        )
                    }
                },
            ) {
                Column {
                    DatePicker(
                        state = endDatePickerState,
                        title = {
                            Text(
                                "什么时候打完的${Emojis.ANGRY}",
                                fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                                modifier = Modifier.padding(PaddingValues(start = 24.dp, end = 12.dp, top = 16.dp))
                            )
                        },
                    )
                    TimeInput(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp),
                        state = endTimePickerState,
                    )
                }
            }
        }

        AnimatedVisibility(selectedStartDateMillis != null) {
            StopBonusTextButton(
                modifier = Modifier.fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                onClick = { showEndDateTime = true }
            ) {
                Text(
                    "什么时候打完的${Emojis.ANGRY}${Emojis.ANGRY}",
                    fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                    fontSize = TextUnit(50f, TextUnitType.Sp),
                    modifier = Modifier
                )
            }
        }

        AnimatedVisibility(selectedEndDateTime != null) {
            Text(
                text = selectedEndDateTime?.let { DateTimeFormatters.formatDateTime(it) } ?: "",
                fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                fontSize = TextUnit(15f, TextUnitType.Sp),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
            )
        }

        val duration = if (selectedStartDateTime != null && selectedEndDateTime != null) {
            Duration.between(selectedStartDateTime, selectedEndDateTime)
        } else null

        AnimatedVisibility(duration != null) {
            // 武器选择
            WeaponSelector(state, weapon, onSelect = { weapon = it })
        }

        AnimatedVisibility(duration != null) {
            // 打分
            ScoreSelector(score)
        }

        AnimatedVisibility(duration != null) {
            // 备注
            OutlinedTextField(
                value = remarkValue,
                onValueChange = {
                    remarkValue =
                        if (it.length <= Limits.REMARK_MAX_LENGTH) it else it.substring(0, Limits.REMARK_MAX_LENGTH)
                },
                label = { Text("备注") },
                placeholder = { Text("备注") },
                supportingText = { Text("${remarkValue.length} / ${Limits.REMARK_MAX_LENGTH}") },
            )
        }

        var recording by remember { mutableStateOf(false) }

        AnimatedVisibility(selectedStartDateTime != null && selectedEndDateTime != null) {
            StopBonusElevatedButton(
                enabled = !recording && duration != null && duration.isPositive(),
                modifier = Modifier.fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                onClick = {
                    if (selectedStartDateTime != null && selectedEndDateTime != null && duration != null) {
                        recording = true
                        scope.launch {
                            try {
                                state.accountState.inAccountTransaction { account ->


                                    BonusRecord.new {
                                        this.account = Account.findById(account.id)!! // TODO null ?
                                        this.duration = duration
                                        this.startTime =
                                            ZonedDateTime.of(selectedStartDateTime, zoneId).toInstant()
                                        this.endTime =
                                            ZonedDateTime.of(selectedEndDateTime, zoneId).toInstant()
                                        this.score = score.value.toUInt()
                                        this.remark = remarkValue
                                        weapon?.also { w ->
                                            Weapon.findById(w.id)?.also {
                                                this.weapons = SizedCollection(listOf(it))
                                            }
                                        }
                                    }
                                }

                                scope.launch {
                                    state.snackbarHostState.showSnackbar(
                                        "记录已保存。你就打吧！",
                                        withDismissAction = true
                                    )
                                }

                                clearStates()

                            } catch (e: Exception) {
                                scope.launch {
                                    state.snackbarHostState.showSnackbar(
                                        "记录失败: ${e.message ?: "未知错误"}",
                                        withDismissAction = true
                                    )
                                }
                            } finally {
                                recording = false
                            }
                        }
                    }
                },
            ) {
                if (duration != null) {
                    if (duration.isNegative) {
                        Text(
                            "时光回溯是吧！${Emojis.ANGRY}",
                            modifier = Modifier
                                .align(Alignment.CenterVertically),
                            fontFamily = FontBTTFamily(),
                            fontSize = TextUnit(50f, TextUnitType.Sp)
                        )
                    } else if (duration.toMinutes() <= 0) {
                        Text(
                            "一分钟都没有？😰",
                            modifier = Modifier
                                .align(Alignment.CenterVertically),
                            fontFamily = FontBTTFamily(),
                            fontSize = TextUnit(50f, TextUnitType.Sp)
                        )
                    } else {
                        Text(
                            "就打就打${Emojis.ANGRY}${Emojis.ANGRY}${Emojis.ANGRY}",
                            modifier = Modifier
                                .align(Alignment.CenterVertically),
                            fontFamily = FontBTTFamily(),
                            fontSize = TextUnit(50f, TextUnitType.Sp)
                        )
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
            expanded = it
        },
    ) {
        OutlinedTextField(
            modifier = Modifier
                .focusable(false)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(.65f),
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
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("无") },
                trailingIcon = { Icon(painterResource(Res.drawable.icon_clear), "Clear icon") },
                onClick = {
                    onSelect(null)
                    value = ""
                    expanded = false
                },
            )

            for (weapon in filteredList) {
                DropdownMenuItem(
                    text = { Text(weapon.name) },
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

        Slider(state = scoreState)
    }


}


// private fun Duration.isPositive(): Boolean = (seconds.toInt() or toNanosPart()) > 0
// (seconds | nanos) > 0
