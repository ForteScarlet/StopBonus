pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.hq.hydraulic.software")
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // https://docs.gradle.com/develocity/gradle/current/gradle-plugin/
    // id("com.gradle.develocity") version("4.5.0")
}

// develocity {
//     // configuration
// }

rootProject.name = "bonus-self-desktop"
