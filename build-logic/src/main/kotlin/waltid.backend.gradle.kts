plugins {
    id("waltid.base")

    kotlin("jvm")
    kotlin("plugin.serialization")
}

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val identityLibs = catalogs.named("identityLibs")
val javaVersion = identityLibs.findVersion("java-service").get().requiredVersion.toInt()

kotlin {
    jvmToolchain(javaVersion)
}

tasks.withType<Zip> {
    isZip64 = true
}

dependencies {
    implementation(kotlin("stdlib"))
}
