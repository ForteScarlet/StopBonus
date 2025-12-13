package view

import MaterialThemeState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.ScaleToBounds
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import database.DatabaseOperator
import database.entity.AccountView
import kotlinx.coroutines.CoroutineScope
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_arrow_back
import org.jetbrains.compose.resources.painterResource
import view.account.AccountHome
import view.account.AccountState
import view.login.LoginState
import view.login.LoginView

class AppState(
    val winState: WindowState,
    val materialThemeState: MaterialThemeState,
    val scope: CoroutineScope,
    val databaseOperator: DatabaseOperator
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Suppress("UnusedReceiverParameter")
@Composable
@Preview
fun FrameWindowScope.App(appState: AppState) {
    val loginState = remember {
        LoginState(
            appState,
            appState.databaseOperator,
        )
    }
    var targetAccount by remember { mutableStateOf<AccountView?>(null) }

    SharedTransitionLayout {
        AnimatedContent(targetAccount) { targetAccountView ->
            when (targetAccountView) {
                null -> {
                    LoginView(
                        state = loginState,
                        shardTitleTransactionScope = this@SharedTransitionLayout,
                        animatedContentScope = this@AnimatedContent,
                        onSelect = {
                            targetAccount = it
                            // navController.navigate("account/${it.id}")
                        }
                    )
                }

                else -> {
                    // val account = targetAccountView

                    // if (targetAccountView != null) {
                    Column {
                        CenterAlignedTopAppBar(
                            title = {
                                val hoverState = remember { MutableInteractionSource() }
                                val hovered by hoverState.collectIsHoveredAsState()
                                val textSizeState = animateFloatAsState(if (hovered) 36f else 30f)

                                Text(
                                    text = targetAccountView.name,
                                    modifier = Modifier
                                        .sharedBounds(
                                            rememberSharedContentState("title-${targetAccountView.id}"),
                                            animatedVisibilityScope = this@AnimatedContent,
                                            resizeMode = ScaleToBounds()
                                        )
                                        .hoverable(hoverState),
                                    //fontFamily = FontBTTFamily(),
                                    fontSize = textSizeState.value.sp
                                )

                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    targetAccount = null
                                    // navController.navigate("home")
                                }) {
                                    Icon(painterResource(Res.drawable.icon_arrow_back), contentDescription = "Back")
                                }
                            },

                            actions = {
                                // Text("Action1")
                                // Text("Action2")
                            }
                        )

                        AccountHome(
                            remember {
                                AccountState(
                                    appState,
                                    appState.databaseOperator,
                                    targetAccountView
                                )
                            })
                    }
                    // }
                }
            }
        }
    }
}
