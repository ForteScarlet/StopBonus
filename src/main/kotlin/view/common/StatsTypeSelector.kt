package view.common

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import view.account.stats.StatsType

/**
 * 统计类型选择器下拉菜单
 *
 * @param currentType 当前选中的类型
 * @param onTypeChange 类型变更回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsTypeSelector(
    currentType: StatsType,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTypeChange: (StatsType) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            value = currentType.title,
            readOnly = true,
            onValueChange = { },
            label = { Text("统计类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            for (t in StatsType.entries) {
                DropdownMenuItem(
                    text = { Text(t.title) },
                    onClick = {
                        onTypeChange(t)
                        onExpandedChange(false)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
