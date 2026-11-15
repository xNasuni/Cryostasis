plugins {
    kotlin("jvm")
}

group = rootProject.property("maven_group") as String
version = rootProject.property("mod_version") as String

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":shared"))
    implementation("io.netty:netty-transport:4.2.6.Final")
    implementation("io.netty:netty-codec-base:4.2.6.Final")
    implementation("org.jline:jline:3.26.3")
    implementation("com.google.code.gson:gson:2.11.0")
}

kotlin {
    jvmToolchain((rootProject.property("java_version") as String).toInt())
}

val commit = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }

tasks.processResources {
    val build = commit.map { "${project.version}-$it" }
    inputs.property("build", build)
    filesMatching("build.txt") {
        expand("build" to build.get())
    }
}

tasks.jar {
    archiveBaseName.set("nieve")
    manifest {
        attributes["Main-Class"] = "win.transgirls.nieve.Nieve"
        attributes["Enable-Native-Access"] = "ALL-UNNAMED"
    }

    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
}
