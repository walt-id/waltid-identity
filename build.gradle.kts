allprojects {

    version = "1.0.0"

    repositories {
        mavenCentral()
    }
}

plugins {
    val kotlinVersion = "1.9.10"
    kotlin("multiplatform") version kotlinVersion apply false
    kotlin("plugin.serialization") version kotlinVersion apply false
    id("com.github.ben-manes.versions") version "0.48.0" apply false
}