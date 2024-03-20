package view.account

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
inline fun AccountDetailView(
    state: PageViewState,
    pages: List<AccountViewPage>,
    selected: AccountViewPage?,
    crossinline onSelect: (AccountViewPage?) -> Unit
) {
    val accountState = state.accountState

    Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
        // 账户信息头部
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(accountState.account.name)
        }

        HorizontalDivider(Modifier.fillMaxWidth(.80f).padding(vertical = 20.dp).align(Alignment.CenterHorizontally))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.forEach { page ->
                NavigationDrawerItem(
                    selected = page == selected,
                    onClick = { onSelect(if (page == selected) null else page) },
                    icon = if (page.isMenuIconSupport) {
                        {
                            page.menuIcon(state)
                        }
                    } else null,
                    label = { page.menuLabel(state) }
                )
            }
        }

    }

}
