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
    id("com.android.application") version "8.1.2" apply false
    val kotlinVersion = "1.9.21"
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("plugin.serialization") version kotlinVersion apply false
    id("com.github.ben-manes.versions") version "0.49.0" apply false
    kotlin("jvm") version kotlinVersion
}
dependencies {
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(8)
}
