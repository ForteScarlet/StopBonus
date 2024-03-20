// package view.account.weapon
//
// import FontBTTFamily
// import FontLXGWNeoXiHeiScreenFamily
// import androidx.compose.animation.AnimatedVisibility
// import androidx.compose.desktop.ui.tooling.preview.Preview
// import androidx.compose.foundation.*
// import androidx.compose.foundation.interaction.MutableInteractionSource
// import androidx.compose.foundation.interaction.collectIsHoveredAsState
// import androidx.compose.foundation.layout.*
// import androidx.compose.foundation.lazy.LazyColumn
// import androidx.compose.foundation.lazy.LazyListState
// import androidx.compose.foundation.lazy.items
// import androidx.compose.foundation.lazy.rememberLazyListState
// import androidx.compose.foundation.text.BasicTextField
// import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.filled.Add
// import androidx.compose.material.icons.filled.Delete
// import androidx.compose.material.icons.filled.Done
// import androidx.compose.material.icons.filled.Warning
// import androidx.compose.material.icons.outlined.Warning
// import androidx.compose.material3.*
// import androidx.compose.runtime.*
// import androidx.compose.runtime.snapshots.SnapshotStateList
// import androidx.compose.runtime.snapshots.SnapshotStateMap
// import androidx.compose.ui.Alignment
// import androidx.compose.ui.Modifier
// import androidx.compose.ui.draw.drawWithContent
// import androidx.compose.ui.geometry.Offset
// import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.unit.dp
// import androidx.compose.ui.window.PopupProperties
// import database.entity.*
// import kotlinx.coroutines.CoroutineScope
// import kotlinx.coroutines.coroutineScope
// import kotlinx.coroutines.launch
// import org.jetbrains.exposed.dao.with
// import org.jetbrains.exposed.sql.SizedCollection
// import org.jetbrains.exposed.sql.SizedIterable
// import view.account.AccountState
//
// private sealed class SelectAuthor {
//     data class NewWithName(val name: String) : SelectAuthor()
//     data class Normal(val author: WeaponAuthor) : SelectAuthor()
// }
//
// /**
//  * 配菜列表视图
//  */
// @Composable
// fun AccountWeaponView(state: AccountState, snackbarHostState: SnackbarHostState) {
//     val scope = rememberCoroutineScope()
//     val weaponList = remember { mutableStateListOf<Weapon>() }
//     val listState = rememberLazyListState()
//
//     var createNewWeapon by remember { mutableStateOf(false) }
//
//     LaunchedEffect(state) {
//         state.inAccountTransaction { account ->
//             val weapons = Weapon
//                 .find { Weapons.account eq account.id }
//                 // .with(Weapon::tags, Weapon::author)
//                 .notForUpdate()
//
//             weaponList.addAll(weapons)
//         }
//     }
//
//     if (createNewWeapon) {
//         NewWeapon(
//             scope,
//             state,
//             snackbarHostState,
//             onDismiss = { createNewWeapon = false },
//             onCreated = { weaponList.add(it) }
//         )
//     }
//
//     Scaffold(
//         floatingActionButtonPosition = FabPosition.End,
//         floatingActionButton = {
//             FloatingActionButton(
//                 modifier = Modifier.padding(bottom = 30.dp, end = 15.dp),
//                 shape = FloatingActionButtonDefaults.largeShape,
//                 onClick = {
//                     createNewWeapon = true
//                 },
//             ) {
//                 Icon(Icons.Filled.Add, "添加")
//             }
//         },
//     ) {
//         if (weaponList.isEmpty()) {
//             ShowEmpty()
//         } else {
//             ShowList(scope, listState, weaponList)
//         }
//     }
// }
//
//
// @Composable
// private inline fun LoadDataList(
//     state: AccountState,
//     crossinline onTags: (SizedIterable<SubWeaponTag>) -> Unit,
//     crossinline onAuthors: (SizedIterable<WeaponAuthor>) -> Unit,
// ) {
//     LaunchedEffect(Unit) {
//         coroutineScope {
//             // load tags
//             launch {
//                 state.inAccountTransaction { account ->
//                     val tags = SubWeaponTag.find {
//                         SubWeaponTags.account eq account.id
//                     }.notForUpdate()
//
//                     onTags(tags)
//                 }
//             }
//
//             // load authors
//             launch {
//                 state.inAccountTransaction { account ->
//                     val authors = WeaponAuthor.find {
//                         WeaponAuthors.account eq account.id
//                     }.notForUpdate()
//
//                     onAuthors(authors)
//                 }
//             }
//
//         }
//     }
// }
//
// @OptIn(ExperimentalMaterial3Api::class)
// @Composable
// private fun AuthorSelector(
//     scope: CoroutineScope,
//     state: AccountState,
//     snackbarHostState: SnackbarHostState,
//     authorList: SnapshotStateList<WeaponAuthor>,
//     selectedAuthorState: MutableState<SelectAuthor?>,
// ) {
//     val authorNames = authorList.mapTo(mutableSetOf()) { it.name }
//     var selectedAuthor by selectedAuthorState
//
//     var expanded by remember { mutableStateOf(false) }
//     var value by remember { mutableStateOf("") }
//
//     ExposedDropdownMenuBox(
//         expanded = expanded,
//         onExpandedChange = {
//             expanded = it
//         },
//     ) {
//         OutlinedTextField(
//             modifier = Modifier.menuAnchor(),
//             value = value,
//             // value = value,
//             onValueChange = {
//                 value = it
//                 expanded = true
//                 if (it.isEmpty()) {
//                     selectedAuthor = null
//                 } else if (it !in authorNames) {
//                     selectedAuthor = SelectAuthor.NewWithName(it)
//                 }
//             },
//             placeholder = { Text("请选择") },
//             label = { Text("选择作者") },
//             singleLine = true,
//             trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
//             supportingText = {
//                 val selectedName = selectedAuthor as? SelectAuthor.NewWithName
//                 AnimatedVisibility(selectedName != null) {
//                     Column {
//                         Text("不存在的作者信息。")
//                         Text("将会在提交时创建作者「${selectedName?.name ?: ""}」")
//                     }
//                 }
//             }
//         )
//
//         // see https://stackoverflow.com/questions/76039608/editable-dynamic-exposeddropdownmenubox-in-jetpack-compose
//         val currentSearchValue = value
//         val filteredList =
//             authorList.filter { currentSearchValue.isEmpty() || it.name.contains(currentSearchValue, true) }
//
//         if (filteredList.isNotEmpty()) {
//             DropdownMenu(
//                 modifier = Modifier
//                     .background(Color.White)
//                     .exposedDropdownSize(true),
//                 properties = PopupProperties(focusable = false),
//                 expanded = expanded,
//                 onDismissRequest = { expanded = false },
//             ) {
//                 for (author in filteredList) {
//                     DropdownMenuItem(
//                         text = { Text(author.name) },
//                         onClick = {
//                             selectedAuthor = SelectAuthor.Normal(author)
//                             value = author.name
//                             expanded = false
//                         },
//                     )
//                 }
//
//             }
//         }
//     }
//
// }
//
// @OptIn(ExperimentalLayoutApi::class)
// @Composable
// private fun TagSelector(
//     scope: CoroutineScope,
//     state: AccountState,
//     snackbarHostState: SnackbarHostState,
//     selectedTags: SnapshotStateMap<Int, Unit>,
//     tagList: SnapshotStateList<SubWeaponTag>,
// ) {
//     // 新增 Tags
//     var addNewTag by remember { mutableStateOf(false) }
//     if (addNewTag) {
//         CreateNewTag(scope, state, snackbarHostState, tagList,
//             onDismiss = {
//                 addNewTag = false
//             },
//             onCreate = {
//                 scope.launch {
//                     snackbarHostState.showSnackbar("创建成功~", withDismissAction = true)
//                 }
//
//                 tagList.add(it)
//             }
//         )
//     }
//
//     // 选择Tag列表
//     ElevatedCard {
//         var search by remember { mutableStateOf("") }
//         Column(
//             modifier = Modifier.padding(20.dp).padding(vertical = 20.dp),
//             horizontalAlignment = Alignment.CenterHorizontally
//         ) {
//             Text(text = "选择Tags", fontSize = MaterialTheme.typography.titleMedium.fontSize)
//             BasicTextField(
//                 value = search,
//                 onValueChange = { search = it },
//                 modifier = Modifier.padding(10.dp),
//                 singleLine = true,
//                 decorationBox = { innerTextField ->
//                     Box(Modifier
//                         .padding(vertical = 4.dp)
//                         .drawWithContent {
//                             drawContent()
//                             drawLine(
//                                 color = Color.LightGray.copy(alpha = .65f),
//                                 start = Offset(-10f, size.height),
//                                 end = Offset(size.width + 10f, size.height),
//                                 strokeWidth = 1f
//                             )
//                         }
//                         .padding(vertical = 6.dp)
//                     ) {
//                         if (search.isEmpty()) {
//                             Text("筛选...", color = Color.DarkGray)
//                         }
//                         innerTextField()
//                     }
//                 }
//                 // supportingText = { Text("筛选") },
//                 // shape = ShapeDefaults.ExtraSmall
//             )
//
//             HorizontalDivider(Modifier.padding(horizontal = 40.dp, vertical = 6.dp))
//
//             FlowRow(
//                 horizontalArrangement = Arrangement.spacedBy(16.dp),
//             ) {
//                 tagList.forEach { tag ->
//                     val authorName = tag.name
//                     val idValue = tag.id.value
//                     val selected = idValue in selectedTags
//                     val displayable =
//                         selected || search.isEmpty() || authorName.contains(search, ignoreCase = true)
//
//                     AnimatedVisibility(displayable) {
//                         var deleteConfirm by remember { mutableStateOf(false) }
//                         if (deleteConfirm) {
//                             DeleteAuthorConfirmAlert(
//                                 scope, state, snackbarHostState, tag,
//                                 onDismiss = { deleteConfirm = false },
//                                 onDelete = { tagList.remove(tag) }
//                             )
//                         }
//
//                         val hoverState = remember { MutableInteractionSource() }
//                         val isHovered by hoverState.collectIsHoveredAsState()
//
//                         ElevatedFilterChip(
//                             modifier = Modifier.hoverable(hoverState),
//                             selected = selected,
//                             onClick = {
//                                 selectedTags.compute(idValue) { _, v ->
//                                     if (v == null) Unit else null
//                                 }
//                             },
//                             label = {
//                                 Text(tag.name)
//                             },
//                             leadingIcon = {
//                                 AnimatedVisibility(selected) {
//                                     Icon(
//                                         imageVector = Icons.Filled.Done,
//                                         contentDescription = "Done icon",
//                                         modifier = Modifier.size(FilterChipDefaults.IconSize)
//                                     )
//                                 }
//                             },
//                             trailingIcon = {
//                                 AnimatedVisibility(!selected && isHovered) {
//                                     IconButton(
//                                         modifier = Modifier.size(FilterChipDefaults.IconSize),
//                                         onClick = {
//                                             deleteConfirm = true
//                                         }) {
//                                         Icon(
//                                             imageVector = Icons.Filled.Delete,
//                                             contentDescription = "Delete icon",
//                                         )
//                                     }
//                                 }
//                             }
//                         )
//                     }
//                 }
//
//                 InputChip(
//                     selected = false,
//                     onClick = {
//                         addNewTag = true
//                     },
//                     label = { Icon(Icons.Filled.Add, "Add icon") }
//                 )
//             }
//
//
//         }
//
//     }
// }
//
//
// @Composable
// private inline fun DeleteAuthorConfirmAlert(
//     scope: CoroutineScope,
//     state: AccountState,
//     snackbarHostState: SnackbarHostState,
//     tag: SubWeaponTag,
//     crossinline onDismiss: () -> Unit,
//     crossinline onDelete: () -> Unit,
// ) {
//     AlertDialog(
//         icon = {
//             Icon(Icons.Filled.Warning, contentDescription = "Warning Icon")
//         },
//         title = {
//             Text(text = "确定删除?")
//         },
//         text = {
//             Column {
//                 Text(text = "确定要删除Tag「${tag.name}」的吗?")
//             }
//         },
//         onDismissRequest = {
//             onDismiss()
//         },
//         confirmButton = {
//             TextButton(
//                 onClick = {
//                     scope.launch {
//                         kotlin.runCatching {
//                             state.database.inSuspendedTransaction {
//                                 tag.delete()
//                             }
//                         }.onFailure { e ->
//                             snackbarHostState.showSnackbar("删除失败!: $e", withDismissAction = true)
//                         }.onSuccess {
//                             onDelete()
//                         }
//
//                         onDismiss()
//                     }
//                 }
//             ) {
//                 Text("确认")
//             }
//         },
//         dismissButton = {
//             TextButton(
//                 onClick = {
//                     onDismiss()
//                 }
//             ) {
//                 Text("取消")
//             }
//         }
//     )
// }
//
// @OptIn(ExperimentalMaterial3Api::class)
// @Composable
// private inline fun CreateNewTag(
//     scope: CoroutineScope,
//     state: AccountState,
//     snackbarHostState: SnackbarHostState,
//     allTags: List<SubWeaponTag>,
//     crossinline onDismiss: () -> Unit,
//     crossinline onCreate: (SubWeaponTag) -> Unit,
// ) {
//     val tagNames = allTags.mapTo(mutableSetOf()) { it.name }
//     var creating by remember { mutableStateOf(false) }
//
//     val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) { it == SheetValue.Hidden }
//     val scrollable = rememberScrollState()
//
//     ModalBottomSheet(
//         dragHandle = null,
//         sheetState = sheetState,
//         modifier = Modifier.padding(top = 30.dp),
//         sheetMaxWidth = state.appState.winState.size.width * 0.7f,
//         onDismissRequest = { onDismiss() },
//     ) {
//         Column(
//             modifier = Modifier.fillMaxSize().padding(horizontal = 60.dp).padding(top = 50.dp)
//                 .verticalScroll(scrollable),
//             verticalArrangement = Arrangement.spacedBy(6.dp),
//             horizontalAlignment = Alignment.CenterHorizontally
//         ) {
//             var tagName by remember { mutableStateOf<String?>(null) }
//             val containsName = tagName?.let { it in tagNames } == true
//
//             val submittable = !containsName && (tagName != null && tagName?.isNotBlank() == true)
//
//             OutlinedTextField(
//                 enabled = !creating,
//                 value = tagName ?: "",
//                 onValueChange = { tagName = it },
//                 label = { Text("Tag") },
//                 suffix = { Text("*", color = Color.Red) },
//                 singleLine = true,
//                 isError = containsName,
//                 supportingText = if (containsName) {
//                     {
//                         Text("Tag已存在", color = Color.Red)
//                     }
//                 } else null
//             )
//
//             HorizontalDivider(Modifier.padding(vertical = 20.dp))
//
//             Row(
//                 horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)
//             ) {
//                 ElevatedButton(
//                     onClick = { onDismiss() },
//                 ) {
//                     Text("取消")
//                 }
//
//                 OutlinedButton(
//                     enabled = !creating && submittable,
//                     onClick = {
//                         creating = true
//                         val newName = tagName ?: run {
//                             scope.launch {
//                                 snackbarHostState.showSnackbar(
//                                     "未填写Tag", withDismissAction = true
//                                 )
//                             }
//                             creating = false
//                             return@OutlinedButton
//                         }
//
//                         scope.launch {
//                             try {
//                                 val new = state.inAccountTransaction { account ->
//                                     SubWeaponTag.new {
//                                         this.account = account
//                                         this.name = newName
//                                     }
//                                 }
//
//                                 onCreate(new)
//                                 onDismiss()
//                             } finally {
//                                 creating = false
//                             }
//                         }
//                     },
//                     colors = ButtonDefaults.outlinedButtonColors()
//                 ) {
//                     Text("添加")
//                 }
//             }
//
//         }
//     }
// }
//
//
// @Composable
// private fun ShowEmpty() {
//     Box(
//         Modifier.fillMaxSize(),
//         contentAlignment = Alignment.Center,
//     ) {
//         Column(horizontalAlignment = Alignment.CenterHorizontally) {
//             Icon(
//                 Icons.Outlined.Warning,
//                 contentDescription = "啥也没有",
//                 modifier = Modifier.size(300.dp),
//                 tint = Color.LightGray.copy(alpha = .25f)
//             )
//
//             Text("欲善其事必利其器", fontFamily = FontLXGWNeoXiHeiScreenFamily)
//         }
//     }
// }
//
//
// @OptIn(ExperimentalLayoutApi::class)
// @Composable
// private fun ShowList(
//     scope: CoroutineScope,
//     listState: LazyListState,
//     weaponList: List<Weapon>
// ) {
//     LazyColumn(
//         state = listState,
//         contentPadding = PaddingValues(20.dp)
//     ) {
//         items(weaponList) { weapon ->
//             ListItemWeapon(scope, weapon)
//         }
//     }
// }
//
//
// @OptIn(ExperimentalLayoutApi::class)
// @Composable
// private fun ListItemWeapon(
//     scope: CoroutineScope,
//     weapon: Weapon,
// ) {
//     val interactionSource = remember { MutableInteractionSource() }
//
//     ListItem(
//         modifier = Modifier.hoverable(interactionSource).clickable { },
//         headlineContent = { Text(weapon.name) },
//         overlineContent = {
//             Text(weapon.author?.name ?: "未知作者")
//         },
//         supportingContent = {
//             FlowRow(
//                 horizontalArrangement = Arrangement.spacedBy(15.dp),
//             ) {
//                 weapon.tags.notForUpdate().forEach { tag ->
//                     ElevatedAssistChip(
//                         onClick = {},
//                         label = { Text(tag.name) }
//                     )
//                 }
//             }
//         }
//     )
// }
