/**
 * 应用程序构建配置常量。
 * 集中管理所有与应用程序相关的基础配置信息。
 */
object AppConfig {
    const val APP_NAME = "StopBonus"
    const val APP_PACKAGE = "love.forte.bonus"
    const val APP_MENU_GROUP = "forteApp"
    const val DEFAULT_VERSION = "1.0.24"

    val appNameWithPackage: String
        get() = "$APP_PACKAGE.$APP_NAME"

    // 属性/环境变量名常量
    object PropertyNames {
        const val VERSION = "appVersion"
        const val VERSION_ENV = "APP_VERSION"
        const val CONVEYOR_EXECUTABLE = "conveyorExecutable"
        const val CONVEYOR_EXECUTABLE_ENV = "CONVEYOR_EXECUTABLE"
    }

    // 元数据
    object Meta {
        const val VENDOR = "Forte Scarlet"
        const val DESCRIPTION = "DO NOT BONUS YOURSELF!"
        const val GITHUB_URL = "https://github.com/ForteScarlet/StopBonus"
        const val DOWNLOAD_URL = "https://fortescarlet.github.io/StopBonus/download"
        const val DEB_MAINTAINER = "ForteScarlet@163.com"
        const val WINDOWS_UPGRADE_UUID = "f4a9a22b-b663-4848-95a8-7c0cf844da3f"
    }
}
