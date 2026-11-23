plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
}

group = "id.walt.json-canonicalization"

kotlin {
    js { outputModuleName = "json-canonicalization" }
    sourceSets {
        commonMain.dependencies {
            implementation(identityLibs.kotlinx.serialization.json)
            // Required by the shared suspend-transform compiler convention, including modules with synchronous APIs.
            implementation(identityLibs.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        getByName("jvmAndroidMain").dependencies { implementation(identityLibs.java.json.canonicalization) }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id JSON canonicalization")
        description.set("Shared RFC 8785 JSON canonicalization for DID and AdES")
    }
}
