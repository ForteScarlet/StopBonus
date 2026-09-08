package view.common

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextRange
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
    val textFieldState = rememberTextFieldState(currentType.title, TextRange.Zero)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        // OutlinedTextField(
        //     modifier = Modifier
        //         .widthIn(min = Dimensions.SelectorMinWidth, max = 180.dp)
        //         .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        //     state = textFieldState,
        //     readOnly = true,
        //     label = { Text("统计类型") },
        //     trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        //     colors = ExposedDropdownMenuDefaults.textFieldColors(),
        // )

        DropdownMenuDecorationBox(value = "统计类型", expanded, true)

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
