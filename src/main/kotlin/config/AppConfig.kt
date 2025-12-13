package config

import kotlinx.serialization.Serializable
import java.time.ZoneId

/**
 * 时区信息，包含ID和本地化显示名称
 */
data class TimezoneInfo(
    val id: String,
    val displayName: String
)

/**
 * 应用配置数据类
 *
 * 遵循容错原则：无效值降级为默认值
 */
@Serializable
data class AppConfig(
    val timezone: String = DEFAULT_TIMEZONE
) {
    /**
     * 获取 ZoneId，带容错机制
     * 如果时区字符串无效，降级为默认时区 Asia/Shanghai
     */
    fun zoneId(): ZoneId = runCatching {
        ZoneId.of(timezone)
    }.getOrElse {
        ZoneId.of(DEFAULT_TIMEZONE)
    }

    companion object {
        const val DEFAULT_TIMEZONE = "Asia/Shanghai"
        val DEFAULT = AppConfig()

        /**
         * 时区ID到中文名称的映射
         */
        private val TIMEZONE_DISPLAY_NAMES = mapOf(
            "Asia/Shanghai" to "中国 - 上海 (UTC+8)",
            "Asia/Tokyo" to "日本 - 东京 (UTC+9)",
            "Asia/Seoul" to "韩国 - 首尔 (UTC+9)",
            "Asia/Singapore" to "新加坡 (UTC+8)",
            "America/New_York" to "美国 - 纽约 (UTC-5/-4)",
            "America/Los_Angeles" to "美国 - 洛杉矶 (UTC-8/-7)",
            "Europe/London" to "英国 - 伦敦 (UTC+0/+1)",
            "Europe/Paris" to "法国 - 巴黎 (UTC+1/+2)",
            "Australia/Sydney" to "澳大利亚 - 悉尼 (UTC+10/+11)",
            "UTC" to "协调世界时 (UTC)"
        )

        /**
         * 常用时区列表（以亚洲时区为主），包含本地化显示名称
         */
        val AVAILABLE_TIMEZONES: List<TimezoneInfo> = listOf(
            "Asia/Shanghai",
            "Asia/Tokyo",
            "Asia/Seoul",
            "Asia/Singapore",
            "America/New_York",
            "America/Los_Angeles",
            "Europe/London",
            "Europe/Paris",
            "Australia/Sydney",
            "UTC"
        ).map { id ->
            TimezoneInfo(id, TIMEZONE_DISPLAY_NAMES[id] ?: id)
        }

        /**
         * 根据时区ID获取显示名称
         */
        fun getTimezoneDisplayName(timezoneId: String): String {
            return TIMEZONE_DISPLAY_NAMES[timezoneId] ?: timezoneId
        }
    }
}
