plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.multiplatform") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.native.cocoapods") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
    id("com.github.ben-manes.versions") apply false
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
