package view.common

import FontLXGWNeoXiHeiScreenFamily
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import common.Emojis
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 统一的日期时间选择对话框
 *
 * @param title 对话框标题（如"什么时候开始打的"）
 * @param datePickerState DatePicker状态
 * @param timePickerState TimePicker状态
 * @param onConfirm 确认选择回调，返回选中的时间
 * @param onDismiss 关闭回调
 * @param onNowSelected "现在"按钮回调（可选）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    title: String,
    datePickerState: DatePickerState,
    timePickerState: TimePickerState,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    onNowSelected: (() -> Unit)? = null,
) {
    DatePickerDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
            if (onNowSelected != null) {
                TextButton(onClick = onNowSelected) {
                    Text(
                        "现在${Emojis.CLOCK}",
                        fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
            }) {
                Text(
                    "就是这时${Emojis.ANGRY}",
                    fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                )
            }
        },
    ) {
        Column {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        "$title${Emojis.ANGRY}",
                        fontFamily = FontLXGWNeoXiHeiScreenFamily(),
                        modifier = Modifier.padding(PaddingValues(start = 24.dp, end = 12.dp, top = 16.dp))
                    )
                },
            )
            TimeInput(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(20.dp),
                state = timePickerState,
            )
        }
    }
}

/**
 * 创建并记住一个 TimePickerState
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberCurrentTimePickerState(): TimePickerState {
    val nowTime = Instant.now().atZone(ZoneId.systemDefault()).toLocalDateTime()
    return rememberTimePickerState(
        is24Hour = true,
        initialHour = nowTime.hour,
        initialMinute = nowTime.minute
    )
}
