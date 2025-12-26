package view.welcome

import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import config.BuildConfig
import view.common.StopBonusTextButton

/**
 * 应用信息对话框
 *
 * 展示版本号和开源地址
 */
@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "关于 ${BuildConfig.APP_NAME}",
                fontFamily = FontLXGWNeoXiHeiScreenFamily()
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 版本号
                Row {
                    Text(
                        "版本: ",
                        fontFamily = FontLXGWNeoXiHeiScreenFamily()
                    )
                    Text(
                        "v${BuildConfig.VERSION}",
                        fontFamily = FontLXGWNeoXiHeiScreenFamily()
                    )
                }

                // 开源地址
                Row {
                    Text(
                        "开源地址: ",
                        fontFamily = FontLXGWNeoXiHeiScreenFamily()
                    )
                    Text(
                        BuildConfig.GITHUB_URL,
                        fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            uriHandler.openUri(BuildConfig.GITHUB_URL)
                        }
                    )
                }
            }
        },
        confirmButton = {
            StopBonusTextButton(onClick = onDismiss) {
                Text("确定", fontFamily = FontLXGWNeoXiHeiScreenFamily())
            }
        }
    )
}
