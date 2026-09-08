@file:OptIn(ExperimentalMaterial3Api::class)

package view.account.stats

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import io.github.koalaplot.core.bar.BarScope
import io.github.koalaplot.core.bar.DefaultBar

@Composable
fun BarScope.DefaultBarWithTooltip(
    brush: Brush,
    shape: Shape,
    tooltip: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { tooltip() },
        state = rememberTooltipState(),
        focusable = false,
    ) {
        DefaultBar(brush = brush, shape = shape)
    }
}
