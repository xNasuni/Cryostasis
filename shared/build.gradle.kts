plugins {
    kotlin("jvm")
}

group = rootProject.property("maven_group") as String
version = rootProject.property("mod_version") as String

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain((rootProject.property("java_version") as String).toInt())
}
