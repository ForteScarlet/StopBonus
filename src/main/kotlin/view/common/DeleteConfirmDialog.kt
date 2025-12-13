package view.common

import FontBTTFamily
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                enabled = !isDeleting,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f)
                )
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Text("删除")
                }
            }
        },
        dismissButton = if (!isDeleting) {
            {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("取消")
                }
            }
        } else null
    )
}
