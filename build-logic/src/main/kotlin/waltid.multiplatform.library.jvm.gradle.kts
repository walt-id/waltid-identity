plugins {
    id("waltid.multiplatform.library.common")
}

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val identityLibs = catalogs.named("identityLibs")
val javaVersion = identityLibs.findVersion("java-library").get().requiredVersion.toInt()

kotlin {
    jvmToolchain(javaVersion)

    jvm()
}
