allprojects {

    version = "1.0.1"

    repositories {
        mavenCentral()
    }
}

plugins {
    val kotlinVersion = "1.9.20"
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("plugin.serialization") version kotlinVersion apply false
    id("com.github.ben-manes.versions") version "0.48.0" apply false
    kotlin("jvm") version "1.9.20"
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
