plugins {
    id("waltid.base")

    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(project.javaServiceVersion)
}

tasks.withType<Zip> {
    isZip64 = true
}

dependencies {
    implementation(kotlin("stdlib"))
}
