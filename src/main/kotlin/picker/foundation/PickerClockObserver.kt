package picker.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import picker.core.PickerEnvironment
import picker.core.PickerNow
import picker.core.sampleNow
import kotlin.time.Duration.Companion.seconds

/**
 * 观察选择器环境，并在组合项存活期间每秒刷新一次一致的当前时间快照。
 */
@Composable
fun rememberPickerNow(environment: PickerEnvironment): PickerNow {
    var now by remember(environment.clock, environment.zoneId) {
        mutableStateOf(environment.sampleNow())
    }

    // 时钟与时区均为键；任一来源变化时立即启动新快照流，避免沿用过期的本地投影。
    LaunchedEffect(environment.clock, environment.zoneId) {
        while (isActive) {
            now = environment.sampleNow()
            delay(CLOCK_REFRESH_INTERVAL)
        }
    }
    return now
}

private val CLOCK_REFRESH_INTERVAL = 1.seconds
