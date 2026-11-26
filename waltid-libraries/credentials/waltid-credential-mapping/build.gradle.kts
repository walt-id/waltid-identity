plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

kotlin {
    js {
        outputModuleName.set("credential-mapping")
    }

    sourceSets {
        commonMain.dependencies {
            api(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.coroutines.core)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Credential Mapping")
        description.set("Format-neutral JSON mapping for digital credentials")
    }
}
