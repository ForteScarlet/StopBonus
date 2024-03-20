package view

import FontBTT
import MaterialThemeState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import database.DatabaseOperator
import database.entity.Account
import kotlinx.coroutines.CoroutineScope
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

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UnusedReceiverParameter")
@Composable
@Preview
fun FrameWindowScope.App(appState: AppState) {
    val targetAccount = remember { mutableStateOf<Account?>(null) }

    AnimatedContent(targetAccount.value) { account ->
        when (account) {
            null -> {
                LoginView(
                    remember {
                        LoginState(
                            appState,
                            appState.databaseOperator,
                        )
                    },
                    onSelect = {
                        targetAccount.value = it
                    }
                )
            }

            else -> {
                Column {
                    CenterAlignedTopAppBar(
                        title = {
                            val hoverState = remember { MutableInteractionSource() }
                            val hovered by hoverState.collectIsHoveredAsState()
                            val textSizeState = animateFloatAsState(
                                if (hovered) 36f else 30f
                            )

                            Text(
                                text = account.name,
                                modifier = Modifier.hoverable(hoverState),
                                fontFamily = FontFamily(FontBTT()),
                                fontSize = TextUnit(textSizeState.value, TextUnitType.Sp)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                targetAccount.value = null
                            }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")

                            }
                        },
                        actions = {
                            // Text("Action1")
                            // Text("Action2")
                        }

                    )

                    AccountHome(
                        remember(account) {
                            AccountState(
                                appState,
                                appState.databaseOperator,
                                account,
                                targetAccount
                            )
                        }
                    )
                }

            }
        }
    }


}
