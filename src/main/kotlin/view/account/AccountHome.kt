package view.account

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import database.DatabaseOperator
import database.entity.AccountView
import org.jetbrains.exposed.sql.Transaction
import view.AppState
import view.account.home.AccountHomeView
import view.account.record.AccountBonusRecordView
import view.account.stats.StatsPageView
import view.account.weapon.AccountWeaponPageView
import kotlin.coroutines.CoroutineContext

@Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
class AccountState(
    val appState: AppState,
    val database: DatabaseOperator,
    val account: AccountView,
    val accountState: MutableState<AccountView?>,
) {
    var accountOrNull by accountState

    suspend inline fun <T> inAccountTransaction(
        context: CoroutineContext? = null,
        transactionIsolation: Int? = null,
        crossinline block: suspend Transaction.(AccountView) -> T
    ): T {
        return account.let { a ->
            database.inSuspendedTransaction(context, transactionIsolation) {
                block(a)
            }
        }
    }
}

class PageViewState(
    val accountState: AccountState,
    val snackbarHostState: SnackbarHostState,
    val drawerState: DrawerState,
    selectedActionState: MutableState<AccountViewPage?>,
) {
    var selectedAction by selectedActionState

}

val pageViews: List<AccountViewPageSelector> = listOf(
    AccountHomeView,
    AccountWeaponPageView,
    AccountBonusRecordView,
    StatsPageView
)

/**
 * 用户信息首页
 */
@Composable
fun AccountHome(state: AccountState) {
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedAccountViewPageState = remember { mutableStateOf<AccountViewPage?>(null) }
    val drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed)

    val pageViewState = remember { PageViewState(state, snackbarHostState, drawerState, selectedAccountViewPageState) }

    var selectedAccountViewPage by selectedAccountViewPageState

    PermanentNavigationDrawer(
        // drawerState = drawerState,
        drawerContent = {
            PermanentDrawerSheet {
                AccountDetailView(
                    pageViewState,
                    pageViews,
                    selectedAccountViewPage,
                    onSelect = { selectedAccountViewPage = it })
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 右边：选中的内容
                Crossfade(
                    targetState = selectedAccountViewPage,
                ) { page ->
                    if (page != null) {
                        Box(
                            modifier = Modifier
                                .animateContentSize()
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            page.rightView(pageViewState)
                        }
                    }
                }
            }

        }
    }


}

