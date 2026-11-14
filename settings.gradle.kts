pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    kotlin("jvm") version "2.4.21" apply false
}

rootProject.name = "cryostasis"

include("mod")
include("nieve")
