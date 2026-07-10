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

allprojects {
    version = "1.0.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
        maven("https://maven.waltid.dev/releases")
        maven("https://maven.waltid.dev/snapshots")
    }
}
