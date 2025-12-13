package view.welcome

import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import config.AppConfig
import config.LocalAppConfig
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_arrow_back
import org.jetbrains.compose.resources.painterResource

/**
 * 配置页面
 *
 * 简洁现代的页面布局，从上往下排列，水平居中
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigPage(onBack: () -> Unit) {
    val configState = LocalAppConfig.current
    var expanded by remember { mutableStateOf(false) }
    var selectedTimezone by remember { mutableStateOf(configState.config.timezone) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部栏：返回按钮 + 标题
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Res.drawable.icon_arrow_back),
                    contentDescription = "返回"
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                "配置",
                fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                fontSize = 20.sp
            )
        }

        HorizontalDivider()

        Spacer(modifier = Modifier.height(48.dp))

        // 内容区域
        Column(
            modifier = Modifier.widthIn(max = 400.dp).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 时区设置
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        label = { Text("时区") },
                        value = AppConfig.getTimezoneDisplayName(selectedTimezone),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        AppConfig.AVAILABLE_TIMEZONES.forEach { tz ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        tz.displayName,
                                        fontFamily = FontLXGWNeoXiHeiScreenFamily()
                                    )
                                },
                                onClick = {
                                    selectedTimezone = tz.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("取消", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(onClick = {
                    configState.updateTimezone(selectedTimezone)
                    onBack()
                }) {
                    Text("保存", fontFamily = FontLXGWNeoXiHeiScreenFamily())
                }
            }
        }
    }
}
