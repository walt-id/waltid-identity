plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

kotlin {
    js {
        outputModuleName = "status-list"
    }

    sourceSets {
        val korlibsMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(identityLibs.korlibs.io)
            }
        }

        commonMain.dependencies {
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
        }

        jsMain.get().dependsOn(korlibsMain)
        if (enableIosBuild) {
            getByName("appleMain").dependsOn(korlibsMain)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Status List")
        description.set("walt.id Kotlin Multiplatform library for credential status-list encoding and decoding")
    }
}
