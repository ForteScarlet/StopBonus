package config

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

private val logger = LoggerFactory.getLogger("ConfigManager")

/**
 * 配置管理器
 *
 * 配置文件存储在应用数据目录的 config.json
 */
object ConfigManager {
    private const val CONFIG_FILE_NAME = "config.json"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true  // 容错：忽略未知字段
        encodeDefaults = true
    }

    /**
     * 获取应用数据存储路径
     *
     * 优先级：
     * 1. DEBUG 模式下使用 ./
     * 2. Windows: %LOCALAPPDATA%/StopBonus/
     * 3. 其他: $HOME/StopBonus/
     * 4. 默认: ./
     */
    private fun appDataPath(): Path {
        if (System.getenv("DEBUG").toBoolean() || System.getProperty("debug").toBoolean()) {
            return Path(".")
        }

        val localAppData = System.getenv("LOCALAPPDATA")
        if (localAppData != null) {
            return Path(localAppData, "StopBonus")
        }

        val userHome = System.getProperty("user.home")
        if (userHome != null) {
            return Path(userHome, "StopBonus")
        }

        return Path(".")
    }

    /**
     * 获取应用数据目录（用于 UI 展示/打开等只读用途）
     */
    fun appDataDir(): Path {
        return appDataPath().toAbsolutePath().normalize()
    }

    /**
     * 获取配置文件路径（应用数据目录）
     */
    fun configFilePath(): Path {
        return appDataDir().resolve(CONFIG_FILE_NAME)
    }

    /**
     * 加载配置
     *
     * 容错机制：
     * - 文件不存在 → 返回默认配置
     * - JSON 解析失败 → 返回默认配置
     */
    fun load(): AppConfig {
        val path = configFilePath()
        return if (path.exists()) {
            runCatching {
                json.decodeFromString<AppConfig>(path.readText())
            }.getOrElse { e ->
                logger.warn("Failed to parse config file, using default: {}", e.message)
                AppConfig.DEFAULT
            }
        } else {
            AppConfig.DEFAULT
        }
    }

    /**
     * 保存配置
     */
    fun save(config: AppConfig) {
        val path = configFilePath()
        runCatching {
            // 确保目录存在
            path.parent?.createDirectories()
            path.writeText(json.encodeToString(AppConfig.serializer(), config))
        }.onFailure { e ->
            logger.error("Failed to save config: {}", e.message)
        }
    }
}
