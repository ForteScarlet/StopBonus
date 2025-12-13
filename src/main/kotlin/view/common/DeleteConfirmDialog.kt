package view.common

import FontBTTFamily
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import love.forte.bonus.bonus_self_desktop.generated.resources.Res
import love.forte.bonus.bonus_self_desktop.generated.resources.icon_warning
import org.jetbrains.compose.resources.painterResource

/**
 * 通用删除确认对话框
 *
 * @param title 对话框标题
 * @param isDeleting 是否正在删除中
 * @param onConfirm 确认删除回调
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun DeleteConfirmDialog(
    title: String,
    isDeleting: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        icon = { Icon(painterResource(Res.drawable.icon_warning), "警告") },
        title = { Text(title, fontFamily = FontBTTFamily()) },
        onDismissRequest = {
            if (!isDeleting) {
                onDismiss()
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting
            ) {
                Text(
                    text = if (isDeleting) "删除中..." else "删除",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = if (!isDeleting) {
            {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
        } else null
    )
}
