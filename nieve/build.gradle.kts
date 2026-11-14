plugins {
    kotlin("jvm")
}

group = rootProject.property("maven_group") as String
version = rootProject.property("mod_version") as String

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain((rootProject.property("java_version") as String).toInt())
}

tasks.jar {
    archiveBaseName.set("nieve")
    manifest {
        attributes["Main-Class"] = "win.transgirls.nieve.Nieve"
    }

    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
}
