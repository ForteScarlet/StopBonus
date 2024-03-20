package view.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import database.DatabaseOperator
import database.entity.Account
import kotlinx.coroutines.launch
import view.AppState

class LoginState(
    val appState: AppState,
    val database: DatabaseOperator,
) {
    val scope get() = appState.scope
}

@Composable
fun LoginView(state: LoginState, onSelect: (Account) -> Unit) {
    val scope by state::scope
    val accountList = remember { mutableStateListOf<Account>() }
    var inDeleting by remember { mutableStateOf(false) }

    suspend fun readData() {
        state.database.inSuspendedTransaction {
            accountList.addAll(Account.all().notForUpdate())
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
                            target.delete()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private inline fun AccountList(
    accounts: List<Account>,
    inDeleting: Boolean,
    crossinline onDelete: (Account) -> Unit = {},
    crossinline onSelect: (Account) -> Unit = {}
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
            items(accounts, key = { it.id.value }) { item ->
                val itemId = item.id.value
                val hoverState = remember(item) { MutableInteractionSource() }
                // val isHovered by hoverState.collectIsHoveredAsState()

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().hoverable(hoverState, enabled = !inDeleting)
                        .animateItemPlacement(),
                    onClick = {
                        showItem = if (showItem != itemId) itemId else null
                    },

                    ) {
                    Column(
                        Modifier.align(Alignment.CenterHorizontally).padding(15.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedContent(showItem == itemId) { isShow ->
                            if (isShow) {
                                var inDeleteConfirm by remember(item) { mutableStateOf(false) }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(item.name)

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
                            } else {
                                Text(item.name)
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
    crossinline onCreate: (Account) -> Unit = {}
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

                            onCreate(new)
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
