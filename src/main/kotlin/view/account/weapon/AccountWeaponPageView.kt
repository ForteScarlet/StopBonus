package view.account.weapon

import FontBTTFamily
import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import database.entity.Account
import database.entity.Weapon
import database.entity.WeaponView
import database.entity.Weapons
import database.entity.toView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import view.account.PageViewState
import view.account.SimpleAccountViewPageSelector


/**
 *
 * @author ForteScarlet
 */
object AccountWeaponPageView : SimpleAccountViewPageSelector {
    override val isMenuIconSupport: Boolean
        get() = true

    @Composable
    override fun menuIcon(state: PageViewState) {
        Icon(Icons.Filled.Face, "Weapons")
    }

    @Composable
    override fun menuLabel(state: PageViewState) {
        Text("武器库")
    }

    @Composable
    override fun rightView(state: PageViewState) {
        WeaponList(state)
    }
}

@Composable
private fun WeaponList(state: PageViewState) {
    val scope = rememberCoroutineScope()
    val weaponList = remember { mutableStateListOf<WeaponView>() }
    val listState = rememberLazyListState()

    LaunchedEffect(state) {
        state.accountState.inAccountTransaction { account ->
            val all = Weapon.find { Weapons.account eq account.id }
                .notForUpdate()
                .map { it.toView() }

            weaponList.addAll(all)
        }
    }

    var createNewWeapon by remember { mutableStateOf(false) }
    if (createNewWeapon) {
        NewWeapon(
            scope = scope,
            state = state,
            onDismiss = { createNewWeapon = false },
            onCreated = { weaponList.add(it) },
        )
    }

    Scaffold(
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.padding(bottom = 30.dp, end = 15.dp),
                shape = FloatingActionButtonDefaults.largeShape,
                onClick = {
                    createNewWeapon = true
                },
            ) {
                Icon(Icons.Filled.Add, "添加")
            }
        },
    ) {

        Crossfade(weaponList.isEmpty()) { isEmpty ->
            if (isEmpty) {
                ShowEmpty()
            } else {
                ShowList(state, scope, listState, weaponList)
            }
        }
    }
}


/**
 * 新增一个武器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private inline fun NewWeapon(
    scope: CoroutineScope,
    state: PageViewState,
    crossinline onDismiss: () -> Unit,
    crossinline onCreated: (WeaponView) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) { it == SheetValue.Hidden }
    val scrollable = rememberScrollState()

    ModalBottomSheet(
        dragHandle = null,
        sheetState = sheetState,
        modifier = Modifier.padding(vertical = 20.dp),
        sheetMaxWidth = state.accountState.appState.winState.size.width * 0.8f,
        onDismissRequest = { onDismiss() },
    ) {
        var name by remember { mutableStateOf("") }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 60.dp).padding(top = 50.dp)
                .verticalScroll(scrollable),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "武器登记",
                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                fontFamily = FontBTTFamily()
            )

            HorizontalDivider(Modifier.padding(horizontal = 35.dp).padding(top = 5.dp, bottom = 10.dp))

            OutlinedTextField(
                name,
                { name = it },
                label = { Text("什么武器?") },
                supportingText = {
                    Column {
                        Text("心爱的武器名")
                        Text("比如「LOVEFACTOR御神子二代」")
                    }
                },
                singleLine = true,

                )

            HorizontalDivider(Modifier.padding(vertical = 20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, alignment = Alignment.CenterHorizontally)
            ) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                    }
                ) {
                    Text("取消")
                }

                OutlinedButton(
                    enabled = !creating && name.isNotBlank(),
                    onClick = {
                        creating = true
                        val newName = name.trim()
                        if (newName.isEmpty()) {
                            scope.launch {
                                state.snackbarHostState.showSnackbar("武器名不可为空", withDismissAction = true)
                            }
                            creating = false
                            return@OutlinedButton
                        }

                        scope.launch {
                            try {
                                state.accountState.inAccountTransaction { account ->
                                    val new = Weapon.new {
                                        this.account = Account.findById(account.id)!! // TODO null ?
                                        this.name = newName
                                    }

                                    onCreated(new.toView())
                                }

                                onDismiss()
                            } catch (e: Exception) {
                                scope.launch {
                                    state.snackbarHostState.showSnackbar("创建失败!\n$e", withDismissAction = true)
                                }
                            } finally {
                                creating = false
                            }
                        }
                    },
                ) {
                    Text("创建")
                }
            }

        }
    }
}


@Composable
private fun ShowEmpty() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = "啥也没有",
                modifier = Modifier.size(300.dp),
                tint = Color.LightGray.copy(alpha = .25f)
            )

            Text("欲善其事必利其器", fontFamily = FontLXGWNeoXiHeiScreenFamily())
        }
    }
}


@Composable
private fun ShowList(
    state: PageViewState,
    scope: CoroutineScope,
    listState: LazyListState,
    weaponList: SnapshotStateList<WeaponView>,
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Top)
    ) {
        items(weaponList) { weapon ->
            ListItemWeapon(state, scope, weapon, onDelete = { weaponList.remove(it) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyItemScope.ListItemWeapon(
    state: PageViewState,
    scope: CoroutineScope,
    weapon: WeaponView,
    onDelete: (WeaponView) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    ElevatedCard(
        modifier = Modifier.animateItem(),
    ) {
        var deleteConfirm by remember { mutableStateOf(false) }
        if (deleteConfirm) {
            var onDeleting by remember(deleteConfirm) { mutableStateOf(false) }
            AlertDialog(
                icon = { Icon(Icons.Filled.Warning, "Warning") },
                title = { Text("删除「${weapon.name}」?", fontFamily = FontBTTFamily()) },
                onDismissRequest = {
                    if (!onDeleting) {
                        deleteConfirm = false
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDeleting = true
                        // do delete
                        scope.launch {
                            val name = weapon.name
                            try {
                                state.accountState.database.inSuspendedTransaction {
                                    Weapons.deleteWhere(limit = 1) { id eq weapon.id }
                                }

                                scope.launch {
                                    state.snackbarHostState.showSnackbar("「$name」已删除", withDismissAction = true)
                                }

                                onDelete(weapon)
                            } finally {
                                deleteConfirm = false
                                onDeleting = false
                            }
                        }
                    }) {
                        Text("删除", color = Color.Red)
                    }
                },
                dismissButton = if (!onDeleting) {
                    {
                        TextButton(onClick = { deleteConfirm = false }) { Text("取消") }
                    }
                } else null
            )
        }

        ListItem(
            modifier = Modifier.hoverable(interactionSource),
            headlineContent = { Text(weapon.name) },
            trailingContent = {
                AnimatedVisibility(isHovered) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete icon",
                        modifier = Modifier
                            .clip(ButtonDefaults.shape)
                            .clickable(isHovered) {
                                deleteConfirm = true
                            }
                    )
                }
            }
        )
    }
}
