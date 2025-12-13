package view.common

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import common.Dimensions
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

/**
 * 月份选择器下拉组件
 *
 * @param selectedMonth 当前选中的月份 (1-12)，null 表示未选择
 * @param year 当前选择的年份，用于计算可选月份范围
 * @param expanded 下拉菜单是否展开
 * @param onExpandedChange 展开状态变更回调
 * @param onMonthChange 月份变更回调
 * @param modifier Modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSelector(
    selectedMonth: Int?,
    year: Int?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onMonthChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = YearMonth.now()

    // 根据年份计算可选月份
    val availableMonths: List<Int> = when {
        year == null -> emptyList()
        year > now.year -> emptyList()
        year == now.year -> (1..now.monthValue).toList()
        else -> (1..12).toList()
    }

    val canExpand = availableMonths.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded && canExpand,
        onExpandedChange = { onExpandedChange(it && canExpand) },
        modifier = modifier
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .widthIn(min = Dimensions.SelectorMinWidth, max = Dimensions.SelectorMaxWidth),
            value = selectedMonth?.let { "${it}月" } ?: "",
            readOnly = true,
            onValueChange = { },
            label = { Text("月") },
            placeholder = { Text(if (canExpand) "选择" else "先选年") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && canExpand) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            singleLine = true,
            enabled = canExpand,
        )

        ExposedDropdownMenu(
            expanded = expanded && canExpand,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            for (month in availableMonths) {
                val displayName = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.CHINA)
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                        onMonthChange(month)
                        onExpandedChange(false)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
