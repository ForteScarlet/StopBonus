import org.gradle.api.Project

/**
 * 解析应用版本号。
 * 按优先级从以下来源获取版本：
 * 1. Gradle 属性 (`-PappVersion=xxx`)
 * 2. 环境变量 `APP_VERSION`
 * 3. GitHub Actions 的 `GITHUB_REF_NAME` (去除 v 前缀)
 * 4. 默认版本
 */
fun Project.resolveAppVersion(defaultVersion: String = AppConfig.DEFAULT_VERSION): String {
    val propertyName = AppConfig.PropertyNames.VERSION
    val envName = AppConfig.PropertyNames.VERSION_ENV

    val fromProperty = providers.gradleProperty(propertyName).orNull?.trim()
    if (!fromProperty.isNullOrEmpty()) return fromProperty

    val fromEnv = providers.environmentVariable(envName).orNull?.trim()
    if (!fromEnv.isNullOrEmpty()) return fromEnv

    val fromTag = providers.environmentVariable("GITHUB_REF_NAME").orNull?.trim()?.removePrefix("v")
    if (!fromTag.isNullOrEmpty()) return fromTag

    return defaultVersion
}
