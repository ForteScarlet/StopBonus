import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

/**
 * 检查文件是否为可用的可执行文件。
 */
fun File.isUsableExecutable(): Boolean = isFile && canExecute()

/**
 * 解析 Conveyor 可执行文件路径。
 * 按优先级从以下来源查找：
 * 1. Gradle 属性 (`-PconveyorExecutable=xxx`)
 * 2. 环境变量 `CONVEYOR_EXECUTABLE`
 * 3. 系统 PATH
 * 4. 常见安装目录 (/opt/homebrew/bin, /usr/local/bin, ~/.local/bin 等)
 * 5. NVM 安装目录
 *
 * @throws GradleException 如果找不到可用的 Conveyor 可执行文件
 */
fun Project.resolveConveyorExecutable(): File {
    val propertyName = AppConfig.PropertyNames.CONVEYOR_EXECUTABLE
    val envName = AppConfig.PropertyNames.CONVEYOR_EXECUTABLE_ENV

    fun findConfiguredExecutable(configuredPath: String?): File? {
        val trimmed = configuredPath?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val resolved = file(trimmed)
        if (resolved.isUsableExecutable()) return resolved
        throw GradleException("Conveyor 可执行文件不可用：$resolved")
    }

    val fromProperty = findConfiguredExecutable(providers.gradleProperty(propertyName).orNull)
    if (fromProperty != null) return fromProperty

    val fromEnv = findConfiguredExecutable(providers.environmentVariable(envName).orNull)
    if (fromEnv != null) return fromEnv

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val executableNames = if (isWindows) {
        listOf("conveyor.cmd", "conveyor.exe", "conveyor.bat", "conveyor")
    } else {
        listOf("conveyor")
    }

    // 从 PATH 查找
    val fromPath = System.getenv("PATH")
        ?.split(File.pathSeparatorChar)
        ?.asSequence()
        ?.flatMap { dir -> executableNames.asSequence().map { name -> File(dir, name) } }
        ?.firstOrNull { it.isUsableExecutable() }
    if (fromPath != null) return fromPath

    // 从常见目录查找
    val homeDir = File(System.getProperty("user.home"))
    val commonDirs = listOf(
        File("/opt/homebrew/bin"),
        File("/usr/local/bin"),
        homeDir.resolve(".local/bin"),
        homeDir.resolve("bin"),
        homeDir.resolve(".volta/bin"),
    )
    val fromCommonDirs = commonDirs
        .asSequence()
        .filter { it.isDirectory }
        .flatMap { dir -> executableNames.asSequence().map { name -> dir.resolve(name) } }
        .firstOrNull { it.isUsableExecutable() }
    if (fromCommonDirs != null) return fromCommonDirs

    // 从 NVM 目录查找
    val nvmDir = homeDir.resolve(".nvm/versions/node")
    val fromNvm = nvmDir.listFiles()
        ?.asSequence()
        ?.filter { it.isDirectory }
        ?.flatMap { nodeDir -> executableNames.asSequence().map { name -> nodeDir.resolve("bin").resolve(name) } }
        ?.firstOrNull { it.isUsableExecutable() }
    if (fromNvm != null) return fromNvm

    val installHint = "建议用 npm 全局安装：npm i -g @hydraulic/conveyor（或显式传入 -P$propertyName=...）"
    throw GradleException("找不到 Conveyor 可执行文件（conveyor）。$installHint")
}
