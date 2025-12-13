package config

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 全局 Clock 提供者
 *
 * 供非 Compose 层使用（如数据库层）。
 * Compose 层应优先使用 LocalAppConfig.current.clock
 */
object ClockProvider {
    @Volatile
    private var _clock: Clock = Clock.systemDefaultZone()

    /**
     * 当前配置的 Clock
     */
    val clock: Clock get() = _clock

    /**
     * 当前配置的时区
     */
    val zoneId: ZoneId get() = _clock.zone

    /**
     * 初始化或更新 Clock
     *
     * @param zoneId 时区 ID
     */
    fun initialize(zoneId: ZoneId) {
        _clock = Clock.system(zoneId)
    }

    /**
     * 获取当前时刻
     */
    fun now(): Instant = Instant.now(_clock)

    /**
     * 获取当前毫秒时间戳
     */
    fun nowMillis(): Long = _clock.millis()
}
