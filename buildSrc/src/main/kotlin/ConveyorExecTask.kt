import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import javax.inject.Inject

/**
 * 执行 Conveyor 命令的自定义任务。
 *
 * 用法示例：
 * ```kotlin
 * tasks.register<ConveyorExecTask>("convey") {
 *     configFile.set("conveyor.conf")
 *     outputDirectory.set(layout.buildDirectory.dir("packages"))
 * }
 * ```
 */
abstract class ConveyorExecTask @Inject constructor(
    private val layout: ProjectLayout
) : DefaultTask() {

    init {
        group = "conveyor"
        description = "执行 Conveyor 打包命令"
    }

    /**
     * Conveyor 配置文件路径（相对于项目根目录）。
     * 如果不设置，则使用默认的 conveyor.conf。
     */
    @get:Input
    @get:Optional
    abstract val configFile: Property<String>

    /**
     * 输出目录，默认为 build/packages。
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * 额外的命令行参数。
     */
    @get:Input
    @get:Optional
    abstract val extraArgs: ListProperty<String>

    /**
     * Conveyor 子命令，默认为 "site"。
     */
    @get:Input
    abstract val subCommand: Property<String>

    init {
        outputDirectory.convention(layout.buildDirectory.dir("packages"))
        subCommand.convention("site")
        extraArgs.convention(emptyList())
    }

    @TaskAction
    fun execute() {
        val javaHome = File(System.getProperty("java.home"))
        val outputDir = outputDirectory.get().asFile

        // Conveyor 默认使用 SAFE_REPLACE：当输出目录内容被改动时会拒绝覆盖。
        // build/ 下的产物可安全重建，因此先清理输出目录，避免 "output dir changed" 导致构建失败。
        project.delete(outputDir)

        val conveyor = project.resolveConveyorExecutable()

        val commandLineArgs = buildList {
            add(conveyor.absolutePath)

            configFile.orNull?.let { config ->
                add("-f")
                add(config)
            }

            add("--console=plain")
            add("--show-log=error")
            add("make")
            add("--output-dir")
            add(outputDir.absolutePath)
            add(subCommand.get())

            addAll(extraArgs.get())
        }

        project.exec {
            workingDir(layout.projectDirectory)
            environment("JAVA_HOME", javaHome.absolutePath)
            commandLine(commandLineArgs)
            standardOutput = System.out
            errorOutput = System.err
        }
    }
}
