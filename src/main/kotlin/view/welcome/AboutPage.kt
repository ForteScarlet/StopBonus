package view.welcome

import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

private const val KIB: Long = 1024L
private const val MIB: Long = 1024L * KIB
private const val GIB: Long = 1024L * MIB

private fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L)
    return when {
        value >= GIB -> String.format("%.1f GiB", value.toDouble() / GIB)
        value >= MIB -> String.format("%.1f MiB", value.toDouble() / MIB)
        value >= KIB -> String.format("%.1f KiB", value.toDouble() / KIB)
        else -> "$value B"
    }
}

/**
 * 关于页面
 *
 * 简洁现代的页面布局，从上往下排列，水平居中
 */
@Composable
fun AboutPage(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val runtimeInfo = remember {
        val runtime = Runtime.getRuntime()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        val max = runtime.maxMemory()
        val used = total - free

        listOf(
            "Java" to buildString {
                append(System.getProperty("java.version").orEmpty())
                val vendor = System.getProperty("java.vendor").orEmpty()
                if (vendor.isNotBlank()) append(" ($vendor)")
            }.ifBlank { "-" },
            "JVM" to buildString {
                append(System.getProperty("java.vm.name").orEmpty())
                val vmVersion = System.getProperty("java.vm.version").orEmpty()
                if (vmVersion.isNotBlank()) append(" $vmVersion")
            }.ifBlank { "-" },
            "系统" to buildString {
                append(System.getProperty("os.name").orEmpty())
                val osVersion = System.getProperty("os.version").orEmpty()
                if (osVersion.isNotBlank()) append(" $osVersion")
                val arch = System.getProperty("os.arch").orEmpty()
                if (arch.isNotBlank()) append(" ($arch)")
            }.ifBlank { "-" },
            "内存" to "已用 ${formatBytes(used)} / 已分配 ${formatBytes(total)}（上限 ${formatBytes(max)}）",
        )
    }

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

            // 运行时信息
            runtimeInfo.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        value,
                        fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                        fontSize = 14.sp
                    )
                }
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

            // 下载页
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "下载页面",
                    fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    BuildConfig.DOWNLOAD_URL,
                    fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri(BuildConfig.DOWNLOAD_URL)
                    }
                )
            }
        }
    }
}
