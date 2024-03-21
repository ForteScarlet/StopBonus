import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // kotlin("jvm")
    // id("org.jetbrains.compose")
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
}

group = "love.forte.bouns"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
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

val versionValue = "1.0.3"
val groupId = "love.forte.app.bonus"

// https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Native_distributions_and_local_execution/README.md
compose.desktop {
    application {
        mainClass = "MainKt"


        nativeDistributions {
            modules("java.sql", "java.naming")

            targetFormats(
                TargetFormat.AppImage,
                TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb
            )
            packageName = "StopBonus"
            packageVersion = versionValue
            vendor = "ForteScarlet"
            description = "别再奖励自己了！"
            copyright = "Copyright © 2024 Forte Scarlet."

            windows {
                packageVersion = versionValue
                exePackageVersion = versionValue
                msiPackageVersion = versionValue
                shortcut = true
                dirChooser = true
                menu = true
                menuGroup = groupId
                perUserInstall = true
                iconFile.set(project.rootDir.resolve("hot.ico"))
                upgradeUuid = "f4a9a22b-b663-4848-95a8-7c0cf844da3f"
            }
        }

        buildTypes.release.proguard {
            obfuscate.set(false)
            optimize.set(false)
        }
    }
}
