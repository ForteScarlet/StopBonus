package view.account.weapon

import FontBTTFamily
import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import database.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_add
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_delete
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_face
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_warning
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import view.account.PageViewState
import view.account.SimpleAccountViewPageSelector
import view.common.DeleteConfirmDialog
import view.common.EmptyState


/**
 *
 * @author ForteScarlet
 */
object AccountWeaponPageView : SimpleAccountViewPageSelector {
    override val isMenuIconSupport: Boolean
        get() = true

    @Composable
    override fun menuIcon(state: PageViewState) {
        Icon(painterResource(Res.drawable.icon_face), "Weapons")
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
                Icon(painterResource(Res.drawable.icon_add), "添加")
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
    EmptyState(
        icon = painterResource(Res.drawable.icon_warning),
        message = "欲善其事必利其器"
    )
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
            DeleteConfirmDialog(
                title = "删除「${weapon.name}」?",
                isDeleting = onDeleting,
                onConfirm = {
                    onDeleting = true
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
                },
                onDismiss = { deleteConfirm = false }
            )
        }

        ListItem(
            modifier = Modifier.hoverable(interactionSource),
            headlineContent = { Text(weapon.name) },
            trailingContent = {
                AnimatedVisibility(isHovered) {
                    Icon(
                        painter = painterResource(Res.drawable.icon_delete),
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
