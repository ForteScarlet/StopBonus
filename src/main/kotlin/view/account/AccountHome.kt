package view.account

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Suppress("MemberVisibilityCanBePrivate")
class AccountState(
    val appState: AppState,
    val database: DatabaseOperator,
    val account: AccountView,
) {
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

