package view.common

import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 通用空状态显示组件
 *
 * @param icon 图标资源
 * @param message 提示消息
 * @param iconSize 图标大小
 * @param iconTint 图标颜色
 */
@Composable
fun EmptyState(
    icon: Painter,
    message: String,
    iconSize: Dp = 300.dp,
    iconTint: Color = Color.LightGray.copy(alpha = .25f)
) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = message,
                modifier = Modifier.size(iconSize),
                tint = iconTint
            )
            Text(message, fontFamily = FontLXGWNeoXiHeiScreenFamily())
        }
    }
}
