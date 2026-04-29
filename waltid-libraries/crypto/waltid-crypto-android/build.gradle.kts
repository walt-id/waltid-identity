import com.android.build.api.dsl.androidLibrary

plugins {
    id("waltid.android.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto"

kotlin {
    androidLibrary {
        namespace = group.toString()
        compileSdk = 34
        minSdk = 30

        withJava()

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        packaging {
            resources {
                excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.oshai.kotlinlogging)
        }
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation("androidx.test.ext:junit:1.2.1")
            implementation("androidx.test:runner:1.6.1")
            implementation("androidx.test:rules:1.6.1")
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.junit.jupiter.api)
            implementation(identityLibs.junit.jupiter.params)
        }
    }
}


mavenPublishing {
    pom {
        name.set("walt.id Crypto Android")
        description.set("walt.id Kotlin/Java Crypto Android library")
    }
}
