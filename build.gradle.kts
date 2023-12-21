allprojects {

    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

plugins {
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
