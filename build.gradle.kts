import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinSerialization)
    // https://conveyor.hydraulic.dev/14.0/configs/maven-gradle/#gradle
    //id("dev.hydraulic.conveyor") version "1.9"
    idea
}

val appName = "StopBonus"
val appPackage = "love.forte.bonus"
val appMenuGroup = "forteApp"
val appNameWithPackage = "$appPackage.$appName"
val appVersion = "1.0.17"

group = appPackage
version = appVersion

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

java {
    toolchain {
        this.languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}


dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
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

// BuildConfig 生成任务
val buildConfigDir = layout.buildDirectory.dir("generated/source/buildConfig/main/kotlin")

val generateBuildConfig by tasks.registering {
    val outputDir = buildConfigDir.get().asFile
    outputs.dir(outputDir)

    doLast {
        val buildConfigFile = File(outputDir, "config/BuildConfig.kt")
        buildConfigFile.parentFile.mkdirs()
        buildConfigFile.writeText(
            """
            package config

            /**
             * 编译时生成的构建配置
             *
             * 由 Gradle 在编译时自动生成，请勿手动修改
             */
            object BuildConfig {
                const val VERSION = "$appVersion"
                const val APP_NAME = "$appName"
                const val GITHUB_URL = "https://github.com/ForteScarlet/StopBonus"
            }
            """.trimIndent()
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(generateBuildConfig)
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir(buildConfigDir)
        }
    }
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
            "-server",
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
