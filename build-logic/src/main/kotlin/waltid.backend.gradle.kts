plugins {
    id("waltid.base")

    kotlin("jvm")
    kotlin("plugin.serialization")
}

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val libs = catalogs.named("identityLibs")
val javaVersion = libs.findVersion("java-backend").get().requiredVersion.toInt()

kotlin {
    jvmToolchain(javaVersion)
}

tasks.withType<Zip> {
    isZip64 = true
}

dependencies {
    implementation(kotlin("stdlib"))
}
