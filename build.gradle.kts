import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

allprojects {

    version = "1.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
        maven("https://maven.waltid.dev/releases") {
            content {
                includeGroup("id.walt")
            }
        }
        maven(url = "https://jitpack.io")
    }
}
val targetVersion = JavaVersion.VERSION_1_8
val toolingRuntime = JavaVersion.VERSION_21

plugins {
    val kotlinVersion = "1.9.24"
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("jvm") version kotlinVersion

    kotlin("plugin.serialization") version kotlinVersion apply false

    id("com.android.library") version "8.2.0" apply false
    id("com.android.application") version "8.2.0" apply false

    id("com.github.ben-manes.versions") version "0.49.0" apply false
}
dependencies {
    implementation(kotlin("stdlib"))
}
repositories {
    mavenCentral()
}
tasks.withType(KotlinCompile::class.java) {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(targetVersion.toString()))
    }
}
kotlin {
    jvmToolchain(toolingRuntime.majorVersion.toInt())
}
java {
    sourceCompatibility = targetVersion
    targetCompatibility = toolingRuntime
}

allprojects {
    tasks.withType<DependencyUpdatesTask> {
        rejectVersionIf {
            listOf("-beta", "-alpha", "-rc").any { it in candidate.version.lowercase() } || candidate.version.takeLast(4).contains("RC")
        }
    }
}
