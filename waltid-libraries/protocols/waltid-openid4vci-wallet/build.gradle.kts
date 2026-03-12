plugins {
    kotlin("jvm") version "2.3.10"
}

group = "id.waltid"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}