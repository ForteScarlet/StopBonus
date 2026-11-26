package view.account.stats

import FontBTTFamily
import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import common.Dimensions
import config.LocalAppConfig
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.Symbol
import io.github.koalaplot.core.bar.BarScope
import io.github.koalaplot.core.bar.GroupedVerticalBarPlot
import io.github.koalaplot.core.bar.VerticalBarPlot
import io.github.koalaplot.core.bar.verticalSolidBar
import io.github.koalaplot.core.legend.ColumnLegend
import io.github.koalaplot.core.style.KoalaPlotTheme
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.CategoryAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberAxisContent
import io.github.koalaplot.core.xygraph.rememberFloatLinearAxisModel
import view.account.PageViewState
import view.account.record.format
import view.common.StatsTypeSelector
import java.time.*
import java.time.format.TextStyle
import java.util.*
import kotlin.math.abs
import kotlin.math.max

/**
 * 年内每月统计
 */
class YearMonthlyModeState(year: Year, type: MutableState<StatsType>) {
    var year: Year by mutableStateOf(year)
    var type by type
}

/**
 * 以年为单位的月份统计
 */
@OptIn(ExperimentalMaterial3Api::class)
class YearMonthlyModeStats(private val yearMonthlyModeState: YearMonthlyModeState) : StatsMode() {
    private var year by yearMonthlyModeState::year
    private var type by yearMonthlyModeState::type

    @Composable
    override fun Label(state: PageViewState) {
        Text("月统计")
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun TopBar(state: PageViewState) {
        val now = YearMonth.now()
        var year by remember { mutableStateOf<Int?>(year.value) }
        var typeExpanded by remember { mutableStateOf(false) }

        val onConfirm: () -> Unit = {
            val yv = year
            if (yv != null) {
                this@YearMonthlyModeStats.year = Year.of(yv)
            }
            this@YearMonthlyModeStats.type = type
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimensions.TopBarHorizontalPadding,
                    vertical = Dimensions.TopBarVerticalPadding
                )
                .onKeyEvent { keyEvent ->
                    if (keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyUp) {
                        if (year != null) {
                            onConfirm()
                        }
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.StandardSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimensions.FlowRowVerticalSpacing),
            ) {

                // 统计类型选择器
                StatsTypeSelector(
                    currentType = type,
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                    onTypeChange = {
                        type = it
                        onConfirm()
                    }
                )

                // 年份输入框
                OutlinedTextField(
                    value = year?.toString() ?: "",
                    onValueChange = { value ->
                        if (value.isEmpty()) {
                            year = null
                            return@OutlinedTextField
                        }
                        value.toIntOrNull()?.also { yearValue ->
                            val newYearValue = when {
                                yearValue > now.year -> now.year
                                yearValue == 0 -> 1
                                yearValue < 0 -> abs(yearValue)
                                else -> yearValue
                            }
                            year = newYearValue
                            if (newYearValue in 2000..2999) {
                                onConfirm()
                            }
                        }
                    },
                    label = { Text("年") },
                    singleLine = true,
                    modifier = Modifier.widthIn(min = Dimensions.SelectorMinWidth, max = Dimensions.YearInputWidth),
                )

                val yv = year

                // 确认按钮
                // StopBonusFilledTonalButton(
                //     enabled = yv != null,
                //     onClick = onConfirm,
                //     modifier = Modifier.align(Alignment.CenterVertically),
                // ) {
                //     Text("确定", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                // }
            }
        }
    }

    @Composable
    override fun Content(state: PageViewState) {
        Crossfade(type) {
            when (type) {
                StatsType.COUNT -> CountContent(state)
                StatsType.DURATION -> DurationContent(state)
            }
        }
    }

    private fun startAndEndInstant(zone: ZoneId): Pair<Instant, Instant> {
        val startDateTime = ZonedDateTime.of(
            LocalDate.ofYearDay(year.value, 1),
            LocalTime.MIN,
            zone
        ).toInstant()

        val endDateTime = ZonedDateTime.of(
            LocalDate.ofYearDay(year.value, year.length()),
            LocalTime.MAX,
            zone
        ).toInstant()

        return startDateTime to endDateTime
    }

    /**
     * 次数统计
     */
    @OptIn(ExperimentalKoalaPlotApi::class)
    @Composable
    private fun CountContent(state: PageViewState) {
        val zoneId = LocalAppConfig.current.zoneId
        val (startDateTime, endDateTime) = startAndEndInstant(zoneId)


        data class Data(
            // 横轴：年月
            val boroughs: List<YearMonth>,
            // 纵轴：数值
            val population: List<Float>,
        )

        var data by remember(type, year) { mutableStateOf<Data?>(null) }

        LaunchedEffect(type, year) {
            val account = state.accountState
            val records = account.database.recordsInRange(account.account.id, startDateTime, endDateTime)
            val storageZone = ZoneId.systemDefault()
            run {
                val dataGroup = records.groupingBy { YearMonth.from(it.startTime.atZone(storageZone)) }.eachCount()

                val boroughs = mutableListOf<YearMonth>()
                val population = mutableListOf<Float>()

                for (month in Month.entries) {
                    val yearMonth = year.atMonth(month)
                    boroughs.add(yearMonth)
                    population.add(dataGroup.getOrDefault(yearMonth, 0).toFloat())
                }

                data = Data(boroughs, population)
            }

        }

        Column(
            verticalArrangement = Arrangement.spacedBy(15.dp, Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$year 奖励次数统计", fontFamily = FontBTTFamily(), fontSize = TextUnit(20f, TextUnitType.Sp))

            val d = data
            if (d != null) {
                KoalaPlotTheme {
                    XYGraph(
                        xAxisModel = remember { CategoryAxisModel(d.boroughs) },
                        yAxisModel = rememberFloatLinearAxisModel(
                            0f..max(1f, d.population.max() / 0.85f),
                            minorTickCount = 0
                        ),
                        xAxisContent = rememberAxisContent(
                            labels = { Text(it.displayMonth()) },
                            title = { Text("月份") },
                        ),
                        yAxisContent = rememberAxisContent(
                            labels = { Text(it.toString()) },
                            title = { Text("奖励次数") },
                        ),
                    ) {
                        VerticalBarPlot(
                            xData = d.boroughs,
                            yData = d.population,
                            barWidth = 0.65f,
                            bar = { _, index, _ ->
                                DefaultBarWithTooltip(
                                    brush = SolidColor(StatsColors.firstColor),
                                    shape = RoundedCornerShape(topStartPercent = 35, topEndPercent = 35),
                                    tooltip = {
                                        ElevatedCard(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(35)),
                                            shape = RoundedCornerShape(35),
                                            colors = CardDefaults.elevatedCardColors(containerColor = Color.LightGray)
                                        ) {
                                            val date = d.boroughs[index]
                                            val value = d.population[index]
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    "月份: ${date.displayMonth()}",
                                                    fontFamily = FontLXGWNeoXiHeiScreenFamily()
                                                )
                                                Text(
                                                    "次数: ${value.toInt()}",
                                                    fontFamily = FontLXGWNeoXiHeiScreenFamily()
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    private val chartColors = arrayOf(
        StatsColors.firstColor,
        StatsColors.secondColor
    )

    @OptIn(ExperimentalKoalaPlotApi::class)
    @Composable
    private fun DurationContent(state: PageViewState) {
        val zoneId = LocalAppConfig.current.zoneId
        val (startDateTime, endDateTime) = startAndEndInstant(zoneId)

        data class Data(
            // 横轴：月份
            val boroughs: List<YearMonth>,
            // 纵轴：数据，[A: 总时长, B: 平均时长], length = boroughs
            val population: List<List<Float>>,
        )

        data class Counter(
            val totalMinutes: Float,
            val avgMinutes: Float,
        )

        var data by remember(type, year) { mutableStateOf<Data?>(null) }

        LaunchedEffect(type, year) {
            val account = state.accountState
            val records = account.database.recordsInRange(account.account.id, startDateTime, endDateTime)
            val storageZone = ZoneId.systemDefault()
            run {
                val dataMap = records.groupBy { YearMonth.from(it.startTime.atZone(storageZone)) }
                    .mapValues { (_, entries) ->
                        val minutes = entries.fold(java.time.Duration.ZERO) { total, entry -> total + entry.duration }
                            .toMinutes().toFloat()
                        Counter(minutes, minutes / entries.size)
                    }


                val boroughs = mutableListOf<YearMonth>()
                val population = mutableListOf<List<Float>>()

                for (month in Month.entries) {
                    val yearMonth = year.atMonth(month)
                    boroughs.add(yearMonth)
                    val counter = dataMap[yearMonth]
                    val pointData = listOf(
                        counter?.totalMinutes ?: 0f,
                        counter?.avgMinutes ?: 0f
                    )
                    population.add(pointData)

                }

                data = Data(boroughs, population)
            }

        }

        Column(
            verticalArrangement = Arrangement.spacedBy(15.dp, Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {


            val d = data
            if (d != null) {
                KoalaPlotTheme {
                    ChartLayout(
                        title = {
                            Text(
                                "$year 奖励时长统计",
                                fontFamily = FontBTTFamily(),
                                fontSize = TextUnit(20f, TextUnitType.Sp)
                            )
                        },
                        legend = {
                            ColumnLegend(
                                modifier = Modifier.padding(16.dp).border(1.dp, Color.Black).padding(16.dp),
                                itemCount = 2,
                                symbol = { Symbol(shape = RectangleShape, fillBrush = SolidColor(chartColors[it])) },
                                label = {
                                    if (it == 0) {
                                        Text("总时长(分钟)", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                                    } else {
                                        Text("平均时长(分钟)", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                                    }
                                }
                            )
                        }
                    ) {
                        XYGraph(
                            xAxisModel = remember(d) { CategoryAxisModel(d.boroughs) },
                            yAxisModel = rememberFloatLinearAxisModel(
                                0f..max(1f, d.population.flatten().max() / 0.85f),
                                minorTickCount = 0
                            ),
                            xAxisContent = rememberAxisContent(
                                labels = { Text(it.displayMonth()) },
                                title = { Text("月份") },
                            ),
                            yAxisContent = rememberAxisContent(
                                labels = { Text(it.toString()) },
                                title = { Text("奖励时长(分钟)") },
                            ),
                        ) {

                            @Composable
                            fun BarScope.Bar(name: String, color: Color, yearMonth: YearMonth, value: Float) {
                                DefaultBarWithTooltip(
                                    brush = SolidColor(color),
                                    shape = RoundedCornerShape(topStartPercent = 35, topEndPercent = 35),
                                    tooltip = {
                                        ElevatedCard(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(35)),
                                            shape = RoundedCornerShape(35),
                                            colors = CardDefaults.elevatedCardColors(containerColor = Color.LightGray)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(name, fontFamily = FontLXGWNeoXiHeiScreenFamily())
                                                Text(
                                                    "月份: ${yearMonth.displayMonth()}",
                                                    fontFamily = FontLXGWNeoXiHeiScreenFamily()
                                                )
                                                Text(
                                                    "时长: ${Duration.ofMinutes(value.toLong()).format()}",
                                                    fontFamily = FontLXGWNeoXiHeiScreenFamily()
                                                )
                                            }
                                        }
                                    }
                                )
                            }

                            GroupedVerticalBarPlot(
                                maxBarGroupWidth = 0.65f,
                                animationSpec = KoalaPlotTheme.animationSpec,
                            ) {
                                // 1: 总
                                series(verticalSolidBar(chartColors[0])) {
                                    d.boroughs.forEachIndexed { index, borough ->
                                        val value = d.population[index][0]

                                        item(borough, 0f, d.population[index][0]) { _, _, _ ->
                                            Bar("总时长(分钟)", chartColors[0], borough, value)
                                        }
                                    }
                                }

                                // 2: 平均
                                series(verticalSolidBar(chartColors[1])) {
                                    d.boroughs.forEachIndexed { index, borough ->
                                        val value = d.population[index][1]
                                        item(borough, 0f, d.population[index][1]) { _, _, _ ->
                                            Bar("平均时长(分钟)", chartColors[1], borough, value)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

}

private fun YearMonth.displayMonth(
    style: TextStyle = TextStyle.SHORT,
    locale: Locale = Locale.CHINA // Locale.getDefault()
): String = month.getDisplayName(style, locale)
