package view.account.stats

import FontBTTFamily
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import database.entity.BonusRecord
import database.entity.BonusRecords
import io.github.koalaplot.core.ChartLayout
import io.github.koalaplot.core.Symbol
import io.github.koalaplot.core.bar.*
import io.github.koalaplot.core.legend.ColumnLegend
import io.github.koalaplot.core.style.KoalaPlotTheme
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.CategoryAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberLinearAxisModel
import org.jetbrains.exposed.sql.and
import view.account.PageViewState
import view.account.record.format
import java.time.*
import kotlin.math.abs
import kotlin.math.max


/**
 * 月份内的每日统计
 */
class MonthDailyModeState(yearMonth: YearMonth, type: StatsType) {
    var yearMonth: YearMonth by mutableStateOf(yearMonth)
    var type by mutableStateOf(type)
}

/**
 * 月份内的每日统计
 */
class MonthDailyModeStats(private val monthDailyModeState: MonthDailyModeState) : StatsMode() {
    private var yearMonth by monthDailyModeState::yearMonth
    private var type by monthDailyModeState::type

    @Composable
    override fun Label(state: PageViewState) {
        Text("日统计")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun TopBar(state: PageViewState) {
        val now = YearMonth.now()
        var year by remember { mutableStateOf<Int?>(yearMonth.year) }
        var month by remember { mutableStateOf<Int?>(yearMonth.monthValue) }

        //var type by remember { mutableStateOf(this.type) }
        var typeExpanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 70.dp, vertical = 50.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterHorizontally)
        ) {

            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it },
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor(),
                    value = type.title,
                    readOnly = true,
                    onValueChange = { typeExpanded = true },
                    label = { Text("统计类型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                )

                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false },
                ) {
                    for (t in StatsType.entries) {
                        DropdownMenuItem(
                            text = { Text(t.title) },
                            onClick = {
                                type = t
                                typeExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }


            OutlinedTextField(
                value = year?.toString() ?: "",
                onValueChange = { value ->
                    if (value.isEmpty()) {
                        year = null
                        return@OutlinedTextField
                    }
                    value.toIntOrNull()?.also { yearValue ->
                        year = when {
                            yearValue > now.year -> now.year
                            yearValue == 0 -> 1
                            yearValue < 0 -> abs(yearValue)
                            else -> yearValue
                        }
                    }
                },
                label = { Text("年") },
                singleLine = true,
            )

            OutlinedTextField(
                value = month?.toString() ?: "",
                onValueChange = { value ->
                    if (value.isEmpty()) {
                        month = null
                        return@OutlinedTextField
                    }
                    value.toIntOrNull()?.also { monthValue ->
                        month = when {
                            year == now.year -> when {
                                monthValue > now.month.value -> now.month.value
                                monthValue < 0 -> abs(monthValue)
                                monthValue == 0 -> 1
                                else -> monthValue
                            }

                            monthValue > 12 -> 12
                            monthValue < 0 -> abs(monthValue)
                            monthValue == 0 -> 1
                            else -> monthValue
                        }
                    }
                },
                label = { Text("月") },
                singleLine = true,
            )

            val yv = year
            val mv = month

            OutlinedButton(
                enabled = yv != null && mv != null,
                onClick = {
                    if (yv != null && mv != null) {
                        yearMonth = YearMonth.of(yv, mv)
                    }
                    this@MonthDailyModeStats.type = type
                },
            ) {
                Text("确定")
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

    @OptIn(ExperimentalKoalaPlotApi::class)
    @Composable
    private fun CountContent(state: PageViewState) {
        val zone = ZoneId.systemDefault()

        val startDate = LocalDate.of(yearMonth.year, yearMonth.month, 1)
        val startDateTime = ZonedDateTime.of(startDate.atTime(0, 0), zone).toInstant()
        val endDate = yearMonth.atEndOfMonth()
        val endDateTime = ZonedDateTime.of(endDate.atTime(23, 59), zone).toInstant()
        val maxDay = endDate.dayOfMonth


        data class Data(
            val boroughs: List<Int>,
            val population: List<Float>,
        )

        var data by remember(type, yearMonth) { mutableStateOf<Data?>(null) }

        LaunchedEffect(type, yearMonth) {
            state.accountState.inAccountTransaction { account ->
                // 根据年月查询所有数据，并按日期统计
                // 直接在代码中聚合
                val allRecords = BonusRecord.find {
                    BonusRecords.account eq account.id and BonusRecords.startTime.between(startDateTime, endDateTime)
                }.notForUpdate()

                // 次数
                val countGroup = allRecords.groupingBy { it.startTime.atZone(zone).toLocalDate().dayOfMonth }
                    .eachCount()


                val boroughs = mutableListOf<Int>()
                val population = mutableListOf<Float>()

                for (i in 1..maxDay) {
                    boroughs.add(i)
                    population.add(countGroup.getOrDefault(i, 0).toFloat())
                }

                data = Data(boroughs, population)
            }

        }

        Column(
            verticalArrangement = Arrangement.spacedBy(15.dp, Alignment.Top),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$yearMonth 奖励次数统计", fontFamily = FontBTTFamily, fontSize = TextUnit(20f, TextUnitType.Sp))

            val d = data
            if (d != null) {
                KoalaPlotTheme {
                    XYGraph(
                        xAxisModel = remember { CategoryAxisModel(d.boroughs) },
                        yAxisModel = rememberLinearAxisModel(0f..max(1f, d.population.max() + 5f), minorTickCount = 0),
                        yAxisTitle = "奖励次数",
                        xAxisTitle = "日期"
                    ) {
                        VerticalBarPlot(
                            xData = d.boroughs,
                            yData = d.population,
                            bar = { index ->
                                DefaultVerticalBar(
                                    brush = SolidColor(Color.Blue),
                                    shape = RoundedCornerShape(topStartPercent = 35, topEndPercent = 35),
                                    hoverElement = {
                                        ElevatedCard(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(35)),
                                            shape = RoundedCornerShape(35),
                                            colors = CardDefaults.elevatedCardColors(containerColor = Color.LightGray)
                                        ) {
                                            val date = d.boroughs[index]
                                            val value = d.population[index]
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text("日期: $yearMonth-$date")
                                                Text("次数: ${value.toInt()}")
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
        Color.Red,
        Color.Blue
    )

    @OptIn(ExperimentalKoalaPlotApi::class, ExperimentalFoundationApi::class)
    @Composable
    private fun DurationContent(state: PageViewState) {
        val zone = ZoneId.systemDefault()

        val startDate = LocalDate.of(yearMonth.year, yearMonth.month, 1)
        val startDateTime = ZonedDateTime.of(startDate.atTime(0, 0), zone).toInstant()
        val endDate = yearMonth.atEndOfMonth()
        val endDateTime = ZonedDateTime.of(endDate.atTime(23, 59), zone).toInstant()
        val maxDay = endDate.dayOfMonth

        data class Data(
            val boroughs: List<Int>,
            val population: List<List<Float>>,
        )

        data class Counter(
            val count: Float,
            val totalMinutes: Float,
            val avgMinutes: Float,
        )

        var data by remember(type, yearMonth) { mutableStateOf<Data?>(null) }

        LaunchedEffect(type, yearMonth) {
            state.accountState.inAccountTransaction { account ->
                // 根据年月查询所有数据，并按日期统计
                // 直接在代码中聚合
                val allRecords = BonusRecord.find {
                    BonusRecords.account eq account.id and BonusRecords.startTime.between(startDateTime, endDateTime)
                }.notForUpdate()

                val countGroup = allRecords.groupBy { it.startTime.atZone(zone).toLocalDate().dayOfMonth }
                    .mapValues { (_, value) ->
                        val count = value.count().toFloat()
                        val totalDurationMinutes =
                            value.map { it.duration }.reduce { a, b -> a + b }
                                .toMinutes().toFloat()

                        val avgDurationMinutes: Float = if (count > 0) {
                            totalDurationMinutes / count
                        } else 0f

                        Counter(count, totalDurationMinutes, avgDurationMinutes)
                    }


                val boroughs = mutableListOf<Int>()
                val population = mutableListOf<List<Float>>()

                for (i in 1..maxDay) {
                    boroughs.add(i)
                    // countGroup.getOrDefault(i, 0).toFloat()
                    val counter = countGroup.getOrDefault(i, null)
                    population.add(listOf(counter?.totalMinutes ?: 0f, counter?.avgMinutes ?: 0f))
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
                                "$yearMonth 奖励时长统计",
                                fontFamily = FontBTTFamily,
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
                                        Text("总时长(分钟)")
                                    } else {
                                        Text("平均时长(分钟)")
                                    }
                                }
                            )
                        }
                    ) {
                        XYGraph(
                            xAxisModel = remember { CategoryAxisModel(d.boroughs) },
                            yAxisModel = rememberLinearAxisModel(
                                0f..max(1f, d.population.flatten().max() + 3f),
                                minorTickCount = 0
                            ),
                            yAxisTitle = "奖励时长",
                            xAxisTitle = "日期"
                        ) {

                            @Composable
                            fun BarScope.Bar(name: String, color: Color, date: Int, value: Float) {
                                DefaultVerticalBar(
                                    brush = SolidColor(color),
                                    shape = RoundedCornerShape(topStartPercent = 35, topEndPercent = 35),
                                    hoverElement = {
                                        ElevatedCard(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(35)),
                                            shape = RoundedCornerShape(35),
                                            colors = CardDefaults.elevatedCardColors(containerColor = Color.LightGray)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(name)
                                                Text("日期: $yearMonth-$date")
                                                Text("时长: ${Duration.ofMinutes(value.toLong()).format()}")
                                            }
                                        }
                                    }
                                )
                            }

                            GroupedVerticalBarPlot {
                                // 1: 总
                                series(solidBar(chartColors[0])) {
                                    d.boroughs.forEachIndexed { index, borough ->
                                        val value = d.population[index][0]

                                        item(borough, 0f, d.population[index][0]) {
                                            Bar("总时长(分钟)", chartColors[0], borough, value)
                                        }
                                    }
                                }

                                // 2: 平均
                                series(solidBar(chartColors[1])) {
                                    d.boroughs.forEachIndexed { index, borough ->
                                        val value = d.population[index][0]
                                        item(borough, 0f, d.population[index][1]) {
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
