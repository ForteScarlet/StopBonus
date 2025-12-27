pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.hq.hydraulic.software")
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    // https://docs.gradle.com/develocity/gradle/current/gradle-plugin/
    id("com.gradle.develocity") version("4.3")
}

develocity {
    // configuration
}

rootProject.name = "bonus-self-desktop"
