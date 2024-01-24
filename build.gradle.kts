allprojects {

    version = "1.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
    }
}

plugins {

    val kotlinVersion = "1.9.21"
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("plugin.serialization") version kotlinVersion apply false
    id("com.github.ben-manes.versions") version "0.49.0" apply false
    kotlin("jvm") version kotlinVersion
    id("org.jetbrains.kotlin.android") version kotlinVersion apply false
    id("com.android.application")
}
dependencies {
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    google()
    mavenCentral()
}
kotlin {
    jvmToolchain(8)
}
