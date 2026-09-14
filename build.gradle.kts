plugins {
    alias(identityLibs.plugins.android.application) apply false
    alias(identityLibs.plugins.kotlin.multiplatform) apply false
    alias(identityLibs.plugins.kotlin.compose) apply false
    alias(identityLibs.plugins.compose.multiplatform) apply false
    alias(identityLibs.plugins.kotlin.serialization) apply false
    alias(identityLibs.plugins.versions) apply false
    alias(identityLibs.plugins.buildconfig) apply false
    alias(identityLibs.plugins.sqldelight) apply false
    id("waltid.licensereport") apply false
}

// License reporting resolves every runtime classpath and downloads every POM in the parent
// chain, so it is opt-in rather than always on:
// ./gradlew -p waltid-identity -PenableLicenseReport=true aggregateDependencyNotices --no-configuration-cache
if (providers.gradleProperty("enableLicenseReport").orNull.toBoolean()) {
    apply(plugin = "waltid.licensereport")

    subprojects {
        if (subprojects.isEmpty()) {
            apply(plugin = "waltid.licensereport")
        }
    }
}

allprojects {
    version = "1.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
        maven("https://maven.waltid.dev/releases")
        maven("https://maven.waltid.dev/snapshots")
    }
}
