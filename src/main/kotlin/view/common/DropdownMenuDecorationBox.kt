package view.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import common.Dimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExposedDropdownMenuBoxScope.DropdownMenuDecorationBox(
    value: String,
    expanded: Boolean,
    canExpand: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            .widthIn(
                min = Dimensions.SelectorMinWidth,
                max = Dimensions.SelectorMaxWidth
            )
            .padding(top = TextFieldDefaults.contentPaddingWithLabel().calculateTopPadding())
    ) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            // 关键：这里不用真正的 TextField
            innerTextField = { Text(value) },
            enabled = canExpand,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            interactionSource = interactionSource,
            label = { Text("月") },
            placeholder = {
                Text(if (canExpand) "选择" else "先选年")
            },

            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded && canExpand
                )
            },

            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),

            container = {
                OutlinedTextFieldDefaults.Container(
                    modifier = Modifier,
                    enabled = canExpand,
                    isError = false,
                    interactionSource = interactionSource,
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
            },
        )
    }
}
