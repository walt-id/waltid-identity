plugins {
    alias(identityLibs.plugins.android.application) apply false
    alias(identityLibs.plugins.kotlin.multiplatform) apply false
    alias(identityLibs.plugins.kotlin.compose) apply false
    alias(identityLibs.plugins.compose.multiplatform) apply false
    alias(identityLibs.plugins.kotlin.serialization) apply false
    alias(identityLibs.plugins.versions) apply false
}

//  Uncomment the following to run the license report
// ./gradlew -p waltid-identity aggregateDependencyNotices --no-configuration-cache
//plugins {
//    id("waltid.licensereport")
//}
//
//subprojects {
//    if (subprojects.isEmpty()) {
//        apply(plugin = "waltid.licensereport")
//    }
//}

// Release version is supplied explicitly by the release operator, e.g.:
//   ./gradlew -PwaltidVersion=1.0.0 publishToMavenCentral
// Ordinary development builds keep the snapshot default.
val waltidVersion: String = providers.gradleProperty("waltidVersion").getOrElse("1.0.0-SNAPSHOT")

allprojects {
    version = waltidVersion

    repositories {
        google()
        mavenCentral()
        maven("https://maven.waltid.dev/releases")
        maven("https://maven.waltid.dev/snapshots")
    }
}

// Prints the Gradle projects selected for Maven Central, so the release operator can review the
// exact artifact set before an upload. Used by scripts/release-maven-central-local.sh.
tasks.register("centralPublishTargets") {
    group = "publishing"
    description = "Lists the projects that publish to Maven Central."
    // Reports the projects that actually own a `publishToMavenCentral` task, rather than
    // re-deriving the selection rule, so this always matches what an upload would do.
    doLast {
        rootProject.allprojects
            .filter { it.tasks.findByName("publishToMavenCentral") != null }
            .map { it.path }
            .sorted()
            .forEach(::println)
    }
}
