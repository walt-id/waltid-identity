import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    alias(identityLibs.plugins.android.application) apply false
    alias(identityLibs.plugins.kotlin.multiplatform) apply false
    alias(identityLibs.plugins.kotlin.compose) apply false
    alias(identityLibs.plugins.compose.multiplatform) apply false
    alias(identityLibs.plugins.kotlin.serialization) apply false
    alias(identityLibs.plugins.buildconfig) apply false
    alias(identityLibs.plugins.sqldelight) apply false
    id("waltid.licensereport") apply false
    alias(identityLibs.plugins.google.services) apply false
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

// Report stable updates while preserving dependencies intentionally on preview tracks.
tasks.withType<DependencyUpdatesTask>().configureEach {
    checkConstraints = true
    // Cinterop commonization resolves derived artifacts, not dependency update candidates.
    filterConfigurations = Spec {
        it.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name != "kotlin-commonized-cinterop"
    }
    rejectPreReleases = true
}
