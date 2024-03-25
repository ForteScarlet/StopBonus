package view.account.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.YearMonth

// TODO
/**
 * 月份内的每日统计
 */
class YearMonthlyModeState(yearMonth: YearMonth, type: StatsType) {
    var yearMonth: YearMonth by mutableStateOf(yearMonth)
    var type by mutableStateOf(type)
}
/**
 * 以年为单位的月份统计
 */
class YearMonthlyModeStats
