import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("net.fabricmc.fabric-loom-remap") version "1.19-SNAPSHOT"
}

version = rootProject.property("mod_version") as String
group = rootProject.property("maven_group") as String

base {
    archivesName.set("cryostasis")
}

val targetJavaVersion = (rootProject.property("java_version") as String).toInt()
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

repositories {
    exclusiveContent {
        // Modern Yarn
        forRepository {
            maven("https://repo.codemc.io/repository/relativitymc/") {
                name = "RelativityMC Maven"
            }
        }
        filter {
            includeGroup("org.relativitymc")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    mappings("org.relativitymc:modern-yarn:${rootProject.property("yarn_mappings")}:v2")

    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${rootProject.property("kotlin_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric_version")}")

    implementation(project(":shared"))
    include(project(":shared"))
}

loom {
    useIntermediateMappings = true
    intermediaryUrl =
        $$"https://repo.codemc.io/repository/relativitymc/org/relativitymc/intermediary/%1$s/intermediary-%1$s-v2.jar"
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

// helps my flowstate when developing :3
val autopush = providers.environmentVariable("xNasuniCryostasisFlowStateAutopushScript")
tasks.register<Exec>("xNasuniCryostasisFlowStateAutopush") {
    dependsOn(tasks.named("remapJar"))
    onlyIf { autopush.isPresent && autopush.get().isNotBlank() }

    workingDir = layout.projectDirectory.asFile

    doFirst {
        val builtJar =
            tasks
                .named("remapJar")
                .get()
                .outputs.files.files
                .first { it.name.endsWith(".jar") }
        commandLine(
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            autopush.get(),
            builtJar.absolutePath
        )
    }
}

tasks.named("build") {
    finalizedBy("xNasuniCryostasisFlowStateAutopush")
}
