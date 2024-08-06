import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

allprojects {
    version = "1.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
        maven(url = "https://maven.walt.id/repository/waltid/")
        maven(url = "https://jitpack.io")
    }
}

plugins {
//    val kotlinVersion = "2.0.0"
    val kotlinVersion = "2.0.20-RC"
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("jvm") version kotlinVersion

    kotlin("plugin.compose") version kotlinVersion apply false

    kotlin("plugin.serialization") version kotlinVersion apply false

    id("com.android.library") version "8.2.2" apply false
    id("com.android.application") version "8.2.2" apply false

    id("com.github.ben-manes.versions") version "0.51.0" apply false
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
