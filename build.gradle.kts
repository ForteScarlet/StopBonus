import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Year
import java.time.ZoneId

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

val appVersion = resolveAppVersion()
val composeVersion = libs.versions.compose.plugin.get()
val material3Version = libs.versions.compose.material3.get()

group = AppConfig.APP_PACKAGE
version = appVersion

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

    linuxAmd64("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$composeVersion", excludeMaterial2)
    linuxAarch64("org.jetbrains.compose.desktop:desktop-jvm-linux-arm64:$composeVersion", excludeMaterial2)
    windowsAmd64("org.jetbrains.compose.desktop:desktop-jvm-windows-x64:$composeVersion", excludeMaterial2)
    windowsAarch64("org.jetbrains.compose.desktop:desktop-jvm-windows-arm64:$composeVersion", excludeMaterial2)
    macAmd64("org.jetbrains.compose.desktop:desktop-jvm-macos-x64:$composeVersion", excludeMaterial2)
    macAarch64("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:$composeVersion", excludeMaterial2)

    implementation("org.jetbrains.compose.ui:ui:$composeVersion")
    implementation("org.jetbrains.compose.ui:ui-util:$composeVersion")
    implementation("org.jetbrains.compose.ui:ui-tooling:$composeVersion")
    // 图标直接在 https://fonts.google.com/icons?hl=zh-cn 按需下载
    implementation("org.jetbrains.compose.material3:material3:$material3Version")
    implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
    implementation("org.jetbrains.compose.animation:animation:$composeVersion")

    implementation(libs.h2db)
    runtimeOnly(libs.logback.classic)

    implementation(libs.kotlinx.coroutine.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hikariCP)
    implementation(libs.bundles.exposed)
    implementation(libs.koalaPlot.core)
}

buildConfig {
    packageName("config")
    useKotlinOutput {
        internalVisibility = true
    }
    documentation.set("编译时生成的构建配置, 编译时自动生成，请勿手动修改")
    buildConfigField("VERSION", appVersion)
    buildConfigField("APP_NAME", AppConfig.APP_NAME)
    buildConfigField("GITHUB_URL", AppConfig.Meta.GITHUB_URL)
    buildConfigField("DOWNLOAD_URL", AppConfig.Meta.DOWNLOAD_URL)
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
        args("--win-help-url", AppConfig.Meta.GITHUB_URL)
        args("--win-shortcut-prompt")

        nativeDistributions {
            modules("java.sql", "java.naming")

            targetFormats(
                TargetFormat.Dmg, TargetFormat.Deb,
                TargetFormat.Rpm, TargetFormat.Pkg,
                TargetFormat.Msi, TargetFormat.Exe
            )

            packageName = AppConfig.APP_NAME
            packageVersion = appVersion
            vendor = AppConfig.Meta.VENDOR
            description = AppConfig.Meta.DESCRIPTION
            copyright =
                "Copyright © 2024-${Year.now(ZoneId.of("Asia/Shanghai")).value} ${AppConfig.Meta.VENDOR}. All rights reserved."

            linux {
                shortcut = true
                menuGroup = AppConfig.APP_MENU_GROUP
                iconFile.set(project.rootDir.resolve("icon.png"))
                debMaintainer = AppConfig.Meta.DEB_MAINTAINER
            }

            macOS {
                bundleID = AppConfig.appNameWithPackage
                iconFile.set(project.rootDir.resolve("icon.icns"))
            }

            windows {
                shortcut = true
                dirChooser = true
                menuGroup = AppConfig.APP_MENU_GROUP
                perUserInstall = true
                menu = true
                iconFile.set(project.rootDir.resolve("icon.ico"))
                upgradeUuid = AppConfig.Meta.WINDOWS_UPGRADE_UUID
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
tasks.register<ConveyorExecTask>("convey") {
    dependsOn("jar", "writeConveyorConfig")
    description = "执行 Conveyor 本地打包"
}

tasks.register<ConveyorExecTask>("conveyCi") {
    dependsOn("jar", "writeConveyorConfig")
    description = "执行 Conveyor CI 打包"
    configFile.set("ci.conveyor.conf")
}
