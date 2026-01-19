plugins {
    id("waltid.android.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto"

android {
    namespace = "id.walt.crypto"
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }

    sourceSets {
        androidMain.dependencies {
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation(identityLibs.oshai.kotlinlogging)
        }
        androidInstrumentedTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            implementation("androidx.test.ext:junit:1.2.1")
            implementation("androidx.test:runner:1.6.1")
            implementation("androidx.test:rules:1.6.1")
        }
        androidUnitTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
            implementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
        }
    }
}


mavenPublishing {
    pom {
        name.set("walt.id Crypto Android")
        description.set("walt.id Kotlin/Java Crypto Android library")
    }
}
