package config

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.ZoneId

/**
 * 全局配置状态
 *
 * 使用 CompositionLocal 实现全局传递
 * 所有组件可通过 LocalAppConfig.current 获取
 */
class AppConfigState(initialConfig: AppConfig) {
    var config by mutableStateOf(initialConfig)
        private set

    /**
     * 获取当前配置的时区
     */
    val zoneId: ZoneId get() = config.zoneId()

    /**
     * 更新时区配置并持久化
     */
    fun updateTimezone(timezone: String) {
        config = config.copy(timezone = timezone)
        ConfigManager.save(config)
    }

    /**
     * 更新配置并持久化
     */
    fun updateConfig(newConfig: AppConfig) {
        config = newConfig
        ConfigManager.save(config)
    }
}

/**
 * CompositionLocal 用于全局传递配置状态
 */
val LocalAppConfig = compositionLocalOf<AppConfigState> {
    error("AppConfigState not provided. Wrap your composable with CompositionLocalProvider.")
}
