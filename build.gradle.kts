import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

allprojects {
    version = "1.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
        maven("https://maven.waltid.dev/releases")
        maven("https://maven.waltid.dev/snapshots")
        maven("https://jitpack.io")
    }
}

plugins {
    val kotlinVersion = "2.1.20"
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.power-assert") version kotlinVersion apply false

    kotlin("plugin.compose") version kotlinVersion apply false

    kotlin("plugin.serialization") version kotlinVersion apply false

    id("love.forte.plugin.suspend-transform") version "2.2.0-0.13.1" apply false

    id("com.android.library") version "8.9.2" apply false
    id("com.android.application") version "8.9.2" apply false

    id("com.github.ben-manes.versions") version "0.52.0" apply false
}
dependencies {
    implementation(kotlin("stdlib"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(8)
}

allprojects {
    tasks.withType<DependencyUpdatesTask> {
        rejectVersionIf {
            listOf("-beta", "-alpha", "-rc").any { it in candidate.version.lowercase() } || candidate.version.takeLast(4).contains("RC")
        }
    }
}
