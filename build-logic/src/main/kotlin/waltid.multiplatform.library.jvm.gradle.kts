plugins {
    id("waltid.multiplatform.library.common")
}

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val libs = catalogs.named("identityLibs")
val javaVersion = libs.findVersion("java-library").get().requiredVersion.toInt()

kotlin {
    jvmToolchain(javaVersion)

    jvm()
}
