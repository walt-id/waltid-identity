import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    `kotlin-dsl`
}

val identityCatalog = extensions.getByType<VersionCatalogsExtension>().named("identityLibs")
val dokkaJacksonPinnedVersion = identityCatalog.findVersion("jackson-core").get().requiredVersion
val dokkaJacksonAffectedModules = listOf(
    "com.fasterxml.jackson.core:jackson-core",
    "com.fasterxml.jackson.core:jackson-databind",
)
val dokkaJacksonForcedCoordinates = dokkaJacksonAffectedModules.map { "$it:$dokkaJacksonPinnedVersion" }

configurations.configureEach {
    // Force-pin vulnerable transitive dependencies brought in by Dokka -> dokka-core.
    // Dokka 2.2.0 brings Jackson 2.15.3, which Snyk flags via:
    // SNYK-JAVA-COMFASTERXMLJACKSONCORE-15365924, -15907551, -17434790,
    // -17440366, -17440598, and -17457695. Reuse the repo-wide Jackson 2.x
    // pin (`jackson-core`) for the build-logic classpath.
    resolutionStrategy.force(*dokkaJacksonForcedCoordinates.toTypedArray())
}

dependencies {
    // Share version catalog with build logic
    implementation(files(identityLibs.javaClass.superclass.protectionDomain.codeSource.location))

    // --- Plugins here: ---

    // Kotlin
    implementation(identityLibs.kotlin.gradle.plugin)

    // Serialization
    implementation(identityLibs.kotlin.plugin.serialization)

    // Ktor
    implementation(identityLibs.ktor.gradle.plugin)
    implementation(identityLibs.jib.gradle.plugin)

    // Publishing
    implementation(identityLibs.vanniktech.publish.plugin)
    implementation(identityLibs.petuska.npm.publish.plugin)

    // Suspend-transform
    implementation(identityLibs.loveforte.suspendtransform)

    // Buildconfig
    implementation(identityLibs.buildconfig.plugin)

    // Documentation
    implementation(identityLibs.dokka.gradle.plugin)

    // Gradle Shadow
    implementation(identityLibs.gradle.shadow)

    // Power-assert
    implementation(identityLibs.powerassert)

    // Dependency information
    implementation(identityLibs.benmanes.versions)
    //implementation(identityLibs.owasp.dependencycheck)
    implementation("com.github.jk1.dependency-license-report:com.github.jk1.dependency-license-report.gradle.plugin:3.1.4")

    // Android
    implementation(identityLibs.android.gradle.plugin)
    implementation(identityLibs.jetbrains.compose)

    // Persistence
    implementation(identityLibs.sqldelight.gradle.plugin)

    // Tests
    implementation(identityLibs.mokkery.gradle.plugin)
}
