import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // kotlin("jvm")
    // id("org.jetbrains.compose")
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
    // https://conveyor.hydraulic.dev/14.0/configs/maven-gradle/#gradle
    //id("dev.hydraulic.conveyor") version "1.9"
    idea
}

val appName = "StopBonus"
val appPackage = "love.forte.bonus"
val appMenuGroup = "forteApp"
val appNameWithPackage = "$appPackage.$appName"
val appVersion = "1.0.9"

group = appPackage
version = appVersion

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

java {
    //toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf("-Xopt-in=kotlin.RequiresOptIn")
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
    implementation(compose.material)
    implementation(compose.material3)

    runtimeOnly(libs.logback.classic)
    runtimeOnly(libs.h2db)

    implementation(libs.hikariCP)
    implementation(libs.bundles.exposed)
    implementation(libs.koalaPlot.core)
}


// https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Native_distributions_and_local_execution/README.md
compose.desktop {
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
            copyright = "Copyright © 2024 Forte Scarlet. All rights reserved."

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
