@file:OptIn(ExperimentalMaterial3Api::class)

package view.account.stats

import androidx.compose.material3.*
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
        state = rememberTooltipState(isPersistent = true),
        focusable = false,
    ) {
        DefaultBar(brush = brush, shape = shape)
    }
}
