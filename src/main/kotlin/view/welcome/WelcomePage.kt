package view.welcome

import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import config.BuildConfig
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_info
import org.jetbrains.compose.resources.painterResource
import update.UpdateCoordinator
import view.common.StopBonusElevatedButton
import view.common.StopBonusOutlinedButton

/**
 * 欢迎区域导航容器
 *
 * 管理 HOME/CONFIG/ABOUT 三个子页面的导航
 */
@Composable
internal fun WelcomeNavHost(
    onEnterApp: () -> Unit,
    updates: UpdateCoordinator? = null,
    onInstallUpdate: () -> Unit = {},
) {
    var currentScreen by remember { mutableStateOf(WelcomeScreen.HOME) }
    val updateState = updates?.state?.collectAsState()?.value
    val version = updateState?.current?.label ?: "v${BuildConfig.VERSION}"
    val hasUpdate = updateState?.newer != null
    val showConfig = { currentScreen = WelcomeScreen.CONFIG }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            when {
                targetState == WelcomeScreen.HOME -> {
                    // 返回首页：从左滑入
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                }
                else -> {
                    // 进入子页面：从右滑入
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it } + fadeOut())
                }
            }
        }
    ) { screen ->
        when (screen) {
            WelcomeScreen.HOME -> WelcomeHomePage(
                onEnter = onEnterApp,
                onConfig = showConfig,
                onAbout = { currentScreen = WelcomeScreen.ABOUT },
                version = version,
                hasUpdate = hasUpdate,
            )
            WelcomeScreen.CONFIG -> ConfigPage(
                onBack = { currentScreen = WelcomeScreen.HOME },
                updates = updates,
                updateState = updateState,
                onInstallUpdate = onInstallUpdate,
            )
            WelcomeScreen.ABOUT -> AboutPage(
                onBack = { currentScreen = WelcomeScreen.HOME },
                version = version,
                hasUpdate = hasUpdate,
                onShowUpdate = showConfig,
            )
        }
    }
}

/**
 * 欢迎主页
 *
 * 页面结构：
 * - 中上部：应用标题和版本
 * - 中间偏下：居中的"进入"和"配置"按钮
 * - 右下角：信息按钮
 */
@Composable
private fun WelcomeHomePage(
    onEnter: () -> Unit,
    onConfig: () -> Unit,
    onAbout: () -> Unit,
    version: String,
    hasUpdate: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 主体内容
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 应用标题
            Text(
                text = "别奖励了!",
                fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                fontSize = 48.sp,
                color = MaterialTheme.colorScheme.primary
            )

            VersionLabel(
                label = version,
                fontSize = 16.sp,
                hasUpdate = hasUpdate,
                onShowUpdate = onConfig,
                modifier = Modifier.padding(bottom = 60.dp),
            )

            // 按钮组
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StopBonusElevatedButton(
                    onClick = onEnter,
                    modifier = Modifier.size(width = 160.dp, height = 60.dp)
                ) {
                    Text(
                        "进入",
                        fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                        fontSize = 24.sp
                    )
                }

                StopBonusOutlinedButton(
                    onClick = onConfig,
                    modifier = Modifier.size(width = 160.dp, height = 60.dp)
                ) {
                    Text(
                        "配置",
                        fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                        fontSize = 24.sp
                    )
                }
            }
        }

        // 右下角信息按钮
        IconButton(
            onClick = onAbout,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.icon_info),
                contentDescription = "关于"
            )
        }
    }
}

// 保留向后兼容的旧 API（已废弃，仅供过渡使用）
@Deprecated("Use WelcomeNavHost instead", ReplaceWith("WelcomeNavHost(onEnterApp = onEnter)"))
@Composable
fun WelcomePage(
    onEnter: () -> Unit,
    onShowInfo: () -> Unit
) {
    WelcomeNavHost(onEnterApp = onEnter)
}
