import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // kotlin("jvm")
    // id("org.jetbrains.compose")
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
}

group = "love.forte.bonus"
version = "1.0.4"

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
        javaParameters = true
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

val projectName = "StopBonus"

// https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Native_distributions_and_local_execution/README.md
compose.desktop {
    application {
        mainClass = "MainKt"
        jvmArgs += listOf(
            "-XX:ErrorFile=.logs/hs_err.log",
            "-XX:-HeapDumpOnOutOfMemoryError",
            "-XX:HeapDumpPath=.logs/dump.hprof",
        )

        nativeDistributions {
            modules("java.sql", "java.naming")

            targetFormats(
                TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm
            )

            packageName = projectName
            packageVersion = project.version.toString()
            vendor = "Forte Scarlet"
            description = "别再奖励自己了！"
            copyright = "Copyright © 2024 Forte Scarlet."

            linux {
                menuGroup = project.group.toString()
                iconFile.set(project.rootDir.resolve("icon.png"))
            }

            macOS {
                bundleID = "${project.group}.$projectName"
                iconFile.set(project.rootDir.resolve("icon.icns"))
            }

            windows {
                // shortcut = true
                dirChooser = true
                menuGroup = project.group.toString()
                iconFile.set(project.rootDir.resolve("icon.ico"))
                upgradeUuid = "f4a9a22b-b663-4848-95a8-7c0cf844da3f"
                exePackageVersion = project.version.toString()
            }
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
            obfuscate.set(false)
            optimize.set(false)
        }
    }
}
