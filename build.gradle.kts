allprojects {

    version = "1.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
    }
}

plugins {

    val kotlinVersion = "1.9.22"
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("plugin.serialization") version kotlinVersion apply false
    id("com.github.ben-manes.versions") version "0.49.0" apply false
    kotlin("jvm") version kotlinVersion
    id("com.android.application") version "8.2.1" apply false
    id("org.jetbrains.kotlin.android") version kotlinVersion apply false
//    val  composeVersion ="1.5.4"
//
//    id("org.jetbrains.compose") version composeVersion apply  false
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
