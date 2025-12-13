package config

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Clock
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

    // 缓存 Clock 实例，仅在时区变化时更新
    private var _cachedClock: Clock = Clock.system(initialConfig.zoneId())

    /**
     * 获取当前配置的时区
     */
    val zoneId: ZoneId get() = config.zoneId()

    /**
     * 获取基于当前时区的 Clock（缓存实例）
     */
    val clock: Clock get() = _cachedClock

    /**
     * 更新时区配置并持久化
     */
    fun updateTimezone(timezone: String) {
        config = config.copy(timezone = timezone)
        _cachedClock = Clock.system(config.zoneId())
        ConfigManager.save(config)
        ClockProvider.initialize(config.zoneId())
    }

    /**
     * 更新配置并持久化
     */
    fun updateConfig(newConfig: AppConfig) {
        val zoneChanged = config.timezone != newConfig.timezone
        config = newConfig
        if (zoneChanged) {
            _cachedClock = Clock.system(config.zoneId())
            ClockProvider.initialize(config.zoneId())
        }
        ConfigManager.save(config)
    }
}

/**
 * CompositionLocal 用于全局传递配置状态
 */
val LocalAppConfig = compositionLocalOf<AppConfigState> {
    error("AppConfigState not provided. Wrap your composable with CompositionLocalProvider.")
}
