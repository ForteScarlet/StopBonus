package view.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import database.DatabaseOperator
import database.entity.Account
import database.entity.AccountView
import database.entity.Accounts
import database.entity.toView
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import view.AppState

class LoginState(
    val appState: AppState,
    val database: DatabaseOperator,
) {
    val scope get() = appState.scope
}

@Composable
fun LoginView(state: LoginState, onSelect: (AccountView) -> Unit) {
    val scope by state::scope
    val accountList = remember { mutableStateListOf<AccountView>() }
    var inDeleting by remember { mutableStateOf(false) }

    suspend fun readData() {
        state.database.inSuspendedTransaction {
            accountList.addAll(Account.all().notForUpdate().map { it.toView() })
        }
    }

    var readDataState by remember { mutableStateOf(Any()) }

    LaunchedEffect(readDataState) {
        accountList.clear()
        readData()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 200.dp).padding(bottom = 50.dp)
    ) {
        NewAccount(
            state,
            onCreate = {
                readDataState = Any()
            })

        AccountList(
            accounts = accountList,
            inDeleting = inDeleting,
            onDelete = { target ->
                inDeleting = true
                scope.launch {
                    try {
                        state.database.inSuspendedTransaction {
                            Accounts.deleteWhere(limit = 1) { id eq target.id }
                            accountList.remove(target)
                        }
                    } finally {
                        inDeleting = false
                    }
                }
            },
            onSelect = onSelect
        )
    }

}

@Composable
private inline fun AccountList(
    accounts: List<AccountView>,
    inDeleting: Boolean,
    crossinline onDelete: (AccountView) -> Unit = {},
    crossinline onSelect: (AccountView) -> Unit = {}
) {
    // val hovering by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        var showItem by remember { mutableStateOf<Int?>(null) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(15.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(accounts, key = { it.id }) { item ->
                val itemId = item.id
                // val hoverState = remember(item) { MutableInteractionSource() }
                // val isHovered by hoverState.collectIsHoveredAsState()

                // Modifier.fillMaxWidth().hoverable(hoverState, enabled = !inDeleting)
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    onClick = {
                        showItem = if (showItem != itemId) itemId else null
                    },

                    ) {
                    Column(
                        Modifier.align(Alignment.CenterHorizontally).padding(15.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(item.name)

                        AnimatedVisibility(showItem == itemId) { // isShow ->
                            var inDeleteConfirm by remember(item) { mutableStateOf(false) }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AnimatedContent(inDeleteConfirm) { isInDeleteConfirm ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(.80f),

                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        if (!isInDeleteConfirm) {
                                            ElevatedButton(
                                                onClick = {
                                                    onSelect(item)
                                                },
                                            ) {
                                                Text("进入")
                                            }
                                        }

                                        ElevatedButton(
                                            onClick = {
                                                if (!isInDeleteConfirm) {
                                                    inDeleteConfirm = true
                                                } else {
                                                    // YES, delete it
                                                    onDelete(item)
                                                }
                                            },
                                            colors = ButtonDefaults.elevatedButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.error,
                                            )
                                        ) {
                                            if (isInDeleteConfirm) {
                                                Text("确认删除?")
                                            } else {
                                                Text("删除")
                                            }
                                        }
                                    }

                                }
                            }

                        }
                    }
                }
            }
        }
    }

}

@Composable
inline fun NewAccount(
    state: LoginState,
    crossinline onCreate: (AccountView) -> Unit = {}
) {
    var inSave by remember { mutableStateOf(false) }
    var nameValue by remember { mutableStateOf("") }

    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = nameValue,
                onValueChange = {
                    nameValue = it
                },
                placeholder = { Text("输入新账户名称") },
                label = { Text("账户名称") },
            )

            Button(
                onClick = {
                    inSave = true
                    state.scope.launch {
                        try {
                            val new = state.database.inSuspendedTransaction {
                                Account.new {
                                    name = nameValue.trim()
                                }
                            }

                            onCreate(new.toView())
                        } finally {
                            inSave = false
                        }
                    }
                },
                enabled = nameValue.isNotBlank() && !inSave,
            ) {
                Text("新增")
            }
        }

    }


}
