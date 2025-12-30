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

plugins {
    id("waltid.repositories")
}

allprojects {
    version = "1.0.0-SNAPSHOT"

    repositories {
        google()
        waltidRepositories()
    }
}
