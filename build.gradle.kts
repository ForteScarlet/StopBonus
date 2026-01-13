import hydraulic.conveyor.gradle.ConveyorConfigTask
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildConfig)
    // https://conveyor.hydraulic.dev/21.0/configs/maven-gradle/#gradle
    alias(libs.plugins.conveyor)
    // https://docs.gradle.com/develocity/gradle/current/gradle-plugin/
    idea
}

val appVersionPropertyName = "appVersion"
val appVersionEnvName = "APP_VERSION"
val conveyorExecutablePropertyName = "conveyorExecutable"
val conveyorExecutableEnvName = "CONVEYOR_EXECUTABLE"
val conveyorJdkVendorPropertyName = "conveyorJdkVendor"
val conveyorJdkVendorEnvName = "CONVEYOR_JDK_VENDOR"

fun Project.resolveAppVersion(defaultVersion: String): String {
    val fromProperty = providers.gradleProperty(appVersionPropertyName).orNull?.trim()
    if (!fromProperty.isNullOrEmpty()) return fromProperty

    val fromEnv = providers.environmentVariable(appVersionEnvName).orNull?.trim()
    if (!fromEnv.isNullOrEmpty()) return fromEnv

    val fromTag = providers.environmentVariable("GITHUB_REF_NAME").orNull?.trim()?.removePrefix("v")
    if (!fromTag.isNullOrEmpty()) return fromTag

    return defaultVersion
}

fun Project.resolveConveyorJdkVendor(defaultVendor: String): String {
    fun normalize(input: String): String = when (input.trim().lowercase()) {
        "azul", "azul systems", "azul-systems" -> "azul systems"
        "adoptium", "temurin", "eclipse" -> "adoptium"
        "amazon", "corretto" -> "amazon"
        "microsoft" -> "microsoft"
        "openjdk" -> "openjdk"
        "jetbrains", "jbr" -> "jetbrains"
        else -> input.trim()
    }

    val fromProperty = providers.gradleProperty(conveyorJdkVendorPropertyName).orNull?.trim()
    if (!fromProperty.isNullOrEmpty()) return normalize(fromProperty)

    val fromEnv = providers.environmentVariable(conveyorJdkVendorEnvName).orNull?.trim()
    if (!fromEnv.isNullOrEmpty()) return normalize(fromEnv)

    return normalize(defaultVendor)
}

fun File.isUsableExecutable(): Boolean = isFile && canExecute()

fun Project.resolveConveyorExecutable(): File {
    fun findConfiguredExecutable(configuredPath: String?): File? {
        val trimmed = configuredPath?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val resolved = file(trimmed)
        if (resolved.isUsableExecutable()) return resolved
        throw GradleException("Conveyor 可执行文件不可用：$resolved")
    }

    val fromProperty = findConfiguredExecutable(providers.gradleProperty(conveyorExecutablePropertyName).orNull)
    if (fromProperty != null) return fromProperty

    val fromEnv = findConfiguredExecutable(providers.environmentVariable(conveyorExecutableEnvName).orNull)
    if (fromEnv != null) return fromEnv

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val executableNames = if (isWindows) listOf("conveyor.cmd", "conveyor.exe", "conveyor.bat", "conveyor") else listOf("conveyor")

    val fromPath = System.getenv("PATH")
        ?.split(File.pathSeparatorChar)
        ?.asSequence()
        ?.flatMap { dir -> executableNames.asSequence().map { name -> File(dir, name) } }
        ?.firstOrNull { it.isUsableExecutable() }
    if (fromPath != null) return fromPath

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

    val nvmDir = homeDir.resolve(".nvm/versions/node")
    val fromNvm = nvmDir.listFiles()
        ?.asSequence()
        ?.filter { it.isDirectory }
        ?.flatMap { nodeDir -> executableNames.asSequence().map { name -> nodeDir.resolve("bin").resolve(name) } }
        ?.firstOrNull { it.isUsableExecutable() }
    if (fromNvm != null) return fromNvm

    val installHint = "建议用 npm 全局安装：npm i -g @hydraulic/conveyor（或显式传入 -P$conveyorExecutablePropertyName=...）"
    throw GradleException("找不到 Conveyor 可执行文件（conveyor）。$installHint")
}

val appName = "StopBonus"
val appPackage = "love.forte.bonus"
val appMenuGroup = "forteApp"
val appNameWithPackage = "$appPackage.$appName"
val defaultAppVersion = "1.0.23"
val appVersion = resolveAppVersion(defaultAppVersion)
val conveyorJdkVendorOverride = run {
    val fromProperty = providers.gradleProperty(conveyorJdkVendorPropertyName).orNull?.trim()
    val fromEnv = providers.environmentVariable(conveyorJdkVendorEnvName).orNull?.trim()
    val raw = fromProperty?.takeIf { it.isNotEmpty() } ?: fromEnv?.takeIf { it.isNotEmpty() }
    if (raw.isNullOrEmpty()) null else resolveConveyorJdkVendor(raw)
}

group = appPackage
version = appVersion

tasks.withType<ConveyorConfigTask>().configureEach {
    // 仅在显式指定时覆盖（避免改变默认产线与缓存命中）。
    conveyorJdkVendorOverride?.let { jvmVendorValue.set(it) }
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}


dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality

    val excludeMaterial2: Action<ExternalModuleDependency> = Action {
        exclude(group = "org.jetbrains.compose.material", module = "material")
        exclude(group = "org.jetbrains.compose.material", module = "material-desktop")
    }

    linuxAmd64(compose.desktop.linux_x64, excludeMaterial2)
    linuxAarch64(compose.desktop.linux_arm64, excludeMaterial2)
    windowsAmd64(compose.desktop.windows_x64, excludeMaterial2)
    windowsAarch64(compose.desktop.windows_arm64, excludeMaterial2)
    macAmd64(compose.desktop.macos_x64, excludeMaterial2)
    macAarch64(compose.desktop.macos_arm64, excludeMaterial2)

    // linuxAmd64(compose.desktop.linux_x64)
    // linuxAarch64(compose.desktop.linux_arm64)
    // windowsAmd64(compose.desktop.windows_x64)
    // windowsAarch64(compose.desktop.windows_arm64)
    // macAmd64(compose.desktop.macos_x64)
    // macAarch64(compose.desktop.macos_arm64)

    // implementation(compose.desktop.currentOs) {
    //     exclude(group = "org.jetbrains.compose.material", module = "material")
    //     exclude(group = "org.jetbrains.compose.material", module = "material-desktop")
    // }

    implementation(compose.ui)
    implementation(compose.uiUtil)
    implementation(compose.uiTooling)
    // implementation(compose.material)
    // 图标直接在 https://fonts.google.com/icons?hl=zh-cn 按需下载
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(compose.animation)

    // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-navigation-routing.html
    // implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")

    implementation(libs.h2db)
    runtimeOnly(libs.logback.classic)

    implementation(libs.kotlinx.coroutine.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hikariCP)
    implementation(libs.bundles.exposed)
    implementation(libs.koalaPlot.core)

    // https://www.sauronsoftware.it/projects/junique/index.php
    // implementation(libs.junique)
}

buildConfig {
    packageName("config")
    useKotlinOutput {
        internalVisibility = true
    }
    documentation.set("编译时生成的构建配置, 编译时自动生成，请勿手动修改")
    buildConfigField("VERSION", appVersion)
    buildConfigField("APP_NAME", appName)
    buildConfigField("GITHUB_URL", "https://github.com/ForteScarlet/StopBonus")
    buildConfigField("DOWNLOAD_URL", "https://fortescarlet.github.io/StopBonus/download")
}

compose.resources {
    // customDirectory(
    //     sourceSetName = "main",
    //     directoryProvider = provider { layout.projectDirectory.dir("src/main/composeResources") }
    // )
}

// https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Native_distributions_and_local_execution/README.md
compose.desktop {
    // TODO https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html#gradle-plugin
    nativeApplication {
    }

    application {
        mainClass = "MainKt"
        jvmArgs += listOf(
            "-XX:ErrorFile=.logs/hs_err.log",
            "-XX:-HeapDumpOnOutOfMemoryError",
            "-XX:HeapDumpPath=.logs/dump.hprof",
        )
        args("--win-help-url", "https://github.com/ForteScarlet/StopBonus")
        args("--win-shortcut-prompt")

        nativeDistributions {
            modules("java.sql", "java.naming")

            targetFormats(
                TargetFormat.Dmg, TargetFormat.Deb,
                TargetFormat.Rpm, TargetFormat.Pkg,
                TargetFormat.Msi, TargetFormat.Exe
            )

            packageName = appName
            packageVersion = appVersion
            vendor = "Forte Scarlet"
            description = "DO NOT BONUS YOURSELF!"
            copyright = "Copyright © 2024-2026 Forte Scarlet. All rights reserved."

            linux {
                shortcut = true
                menuGroup = appMenuGroup
                iconFile.set(project.rootDir.resolve("icon.png"))
                debMaintainer = "ForteScarlet@163.com"
            }

            macOS {
                bundleID = appNameWithPackage
                iconFile.set(project.rootDir.resolve("icon.icns"))
            }

            windows {
                shortcut = true
                dirChooser = true
                menuGroup = appMenuGroup
                perUserInstall = true
                menu = true
                iconFile.set(project.rootDir.resolve("icon.ico"))
                upgradeUuid = "f4a9a22b-b663-4848-95a8-7c0cf844da3f"
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
            obfuscate.set(false)
            optimize.set(false)
        }
    }
}

idea {
    this.module {
        isDownloadSources = true
    }
}

// https://conveyor.hydraulic.dev/21.0/configs/maven-gradle/#gradle
tasks.register<Exec>("convey") {
    group = "conveyor"

    val outputDir = layout.buildDirectory.dir("packages")
    outputs.dir(outputDir)
    dependsOn("jar", "writeConveyorConfig")

    workingDir(layout.projectDirectory)
    standardOutput = System.out
    errorOutput = System.err
    doFirst {
        val javaHome = file(System.getProperty("java.home"))
        environment("JAVA_HOME", javaHome.absolutePath)

        val dir = outputDir.get().asFile
        // Conveyor 默认使用 SAFE_REPLACE：当输出目录内容被改动时会拒绝覆盖。
        // `build/` 下的产物可安全重建，因此这里先清理输出目录，避免 “output dir changed” 导致构建失败。
        project.delete(dir)
        val conveyor = resolveConveyorExecutable()
        commandLine(
            conveyor.absolutePath,
            "--console=plain",
            "--show-log=error",
            "make",
            "--output-dir",
            dir.absolutePath,
            "site"
        )
    }
}


tasks.register<Exec>("conveyCi") {
    group = "conveyor"

    val outputDir = layout.buildDirectory.dir("packages")
    outputs.dir(outputDir)
    dependsOn("jar", "writeConveyorConfig")

    workingDir(layout.projectDirectory)
    standardOutput = System.out
    errorOutput = System.err
    doFirst {
        val javaHome = file(System.getProperty("java.home"))
        environment("JAVA_HOME", javaHome.absolutePath)

        val dir = outputDir.get().asFile
        // Conveyor 默认使用 SAFE_REPLACE：当输出目录内容被改动时会拒绝覆盖。
        // `build/` 下的产物可安全重建，因此这里先清理输出目录，避免 “output dir changed” 导致构建失败。
        project.delete(dir)
        val conveyor = resolveConveyorExecutable()

        val commandLineArgs = buildList {
            add(conveyor.absolutePath)
            add("-f ci.conveyor.conf")
            add("--console=plain")
            add("--show-log=error")
            add("make")
            add("--output-dir")
            add(dir.absolutePath)
            add("site")
        }
        commandLine(commandLineArgs)

    }
}
