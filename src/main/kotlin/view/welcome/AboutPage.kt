package view.welcome

import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import config.BuildConfig
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_arrow_back
import org.jetbrains.compose.resources.painterResource

/**
 * 关于页面
 *
 * 简洁现代的页面布局，从上往下排列，水平居中
 */
@Composable
fun AboutPage(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部栏：返回按钮 + 标题
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Res.drawable.icon_arrow_back),
                    contentDescription = "返回"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                "关于",
                fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                fontSize = 20.sp
            )
        }

        HorizontalDivider()

        Spacer(modifier = Modifier.height(48.dp))

        // 内容区域
        Column(
            modifier = Modifier.widthIn(max = 400.dp).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 应用名称
            Text(
                BuildConfig.APP_NAME,
                fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // 版本号
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "版本",
                    fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "v${BuildConfig.VERSION}",
                    fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                    fontSize = 14.sp
                )
            }

            // 开源地址
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "项目地址",
                    fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    BuildConfig.GITHUB_URL,
                    fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri(BuildConfig.GITHUB_URL)
                    }
                )
            }
        }
    }
}
