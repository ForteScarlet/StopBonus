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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
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
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.month
import org.jetbrains.exposed.sql.javatime.year
import view.account.PageViewState
import view.account.record.format
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

    @Composable
    override fun TopBar(state: PageViewState) {
        val now = YearMonth.now()
        var year by remember { mutableStateOf<Int?>(year.value) }
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

            val yv = year

            OutlinedButton(
                enabled = yv != null,
                onClick = {
                    if (yv != null) {
                        this@YearMonthlyModeStats.year = Year.of(yv)
                    }
                    this@YearMonthlyModeStats.type = type
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

    private fun startAndEndInstant(zone: ZoneId = ZoneId.systemDefault()): Pair<Instant, Instant> {
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
        val (startDateTime, endDateTime) = startAndEndInstant()


        data class Data(
            // 横轴：年月
            val boroughs: List<YearMonth>,
            // 纵轴：数值
            val population: List<Float>,
        )

        var data by remember(type, year) { mutableStateOf<Data?>(null) }

        LaunchedEffect(type, year) {
            state.accountState.inAccountTransaction { account ->
                addLogger(StdOutSqlLogger)
                // 根据年查询所有数据，并按月统计
                // 数据：年月 / 次数

                val idCount = BonusRecords.id.count()
                val stYear = BonusRecords.startTime.year()
                val stMonth = BonusRecords.startTime.month()

                val query = BonusRecords
                    .select(idCount, stYear, stMonth)
                    .where {
                        BonusRecords.account eq account.id and BonusRecords.startTime.between(
                            startDateTime,
                            endDateTime
                        )
                    }
                    .groupBy(stYear, stMonth).notForUpdate()

                data class DataRow(val yearMonth: YearMonth, val count: Long)

                val dataGroup = query.map { row ->
                    val count = row[idCount]
                    val year = row[stYear]
                    val month = row[stMonth]

                    println("year=$year, month=$month, count=$count")

                    DataRow(YearMonth.of(year, month), count)
                }.associate { it.yearMonth to it.count }

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
            Text("$year 奖励次数统计", fontFamily = FontBTTFamily, fontSize = TextUnit(20f, TextUnitType.Sp))

            val d = data
            if (d != null) {
                KoalaPlotTheme {
                    XYGraph(
                        xAxisModel = remember { CategoryAxisModel(d.boroughs) },
                        xAxisLabels = { it.displayMonth() },
                        yAxisModel = rememberLinearAxisModel(
                            0f..max(1f, d.population.max() / 0.85f),
                            minorTickCount = 0
                        ),
                        yAxisTitle = "奖励次数",
                        xAxisTitle = "月份"
                    ) {
                        VerticalBarPlot(
                            xData = d.boroughs,
                            yData = d.population,
                            barWidth = 0.65f,
                            bar = { index ->
                                DefaultVerticalBar(
                                    brush = SolidColor(StatsColors.firstColor),
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
                                                Text(
                                                    "月份: ${date.displayMonth()}",
                                                    fontFamily = FontLXGWNeoXiHeiScreenFamily
                                                )
                                                Text(
                                                    "次数: ${value.toInt()}",
                                                    fontFamily = FontLXGWNeoXiHeiScreenFamily
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
        val (startDateTime, endDateTime) = startAndEndInstant()

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
            state.accountState.inAccountTransaction { account ->
                // 根据年查询所有数据，并按月份统计
                val stYear = BonusRecords.startTime.year()
                val stMonth = BonusRecords.startTime.month()

                val totalDuration = BonusRecords.duration.sum()
                val idCount = BonusRecords.id.count()

                val query = BonusRecords.select(
                    idCount, stYear, stMonth, totalDuration
                ).where {
                    BonusRecords.account eq account.id and BonusRecords.startTime.between(startDateTime, endDateTime)
                }.groupBy(stYear, stMonth)

                data class DataRow(val yearMonth: YearMonth, val counter: Counter)

                val dataMap = query.map { row ->
                    val count = row[idCount]
                    val year = row[stYear]
                    val month = row[stMonth]
                    val sumD = row[totalDuration]

                    val totalMinutes = sumD?.toMinutes()?.toFloat() ?: 0f
                    val avgMinutes = if (count > 0L) {
                        totalMinutes / count.toFloat()
                    } else 0f

                    val counter = Counter(
                        totalMinutes = totalMinutes,
                        avgMinutes = avgMinutes,
                    )

                    DataRow(YearMonth.of(year, month), counter)
                }.associate { it.yearMonth to it.counter }


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
                                        Text("总时长(分钟)", fontFamily = FontLXGWNeoXiHeiScreenFamily)
                                    } else {
                                        Text("平均时长(分钟)", fontFamily = FontLXGWNeoXiHeiScreenFamily)
                                    }
                                }
                            )
                        }
                    ) {
                        XYGraph(
                            xAxisModel = remember(d) { CategoryAxisModel(d.boroughs) },
                            xAxisLabels = { it.displayMonth() },
                            yAxisModel = rememberLinearAxisModel(
                                0f..max(1f, d.population.flatten().max() / 0.85f),
                                minorTickCount = 0
                            ),
                            yAxisTitle = "奖励时长(分钟)",
                            xAxisTitle = "月份"
                        ) {

                            @Composable
                            fun BarScope.Bar(name: String, color: Color, yearMonth: YearMonth, value: Float) {
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
                                                Text(name, fontFamily = FontLXGWNeoXiHeiScreenFamily)
                                                Text(
                                                    "月份: ${yearMonth.displayMonth()}",
                                                    fontFamily = FontLXGWNeoXiHeiScreenFamily
                                                )
                                                Text(
                                                    "时长: ${Duration.ofMinutes(value.toLong()).format()}",
                                                    fontFamily = FontLXGWNeoXiHeiScreenFamily
                                                )
                                            }
                                        }
                                    }
                                )
                            }

                            GroupedVerticalBarPlot(maxBarGroupWidth = 0.65f) {
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
                                        val value = d.population[index][1]
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

private fun YearMonth.displayMonth(
    style: TextStyle = TextStyle.SHORT,
    locale: Locale = Locale.CHINA // Locale.getDefault()
): String = month.getDisplayName(style, locale)
