import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("maven-publish")
    id("com.github.ben-manes.versions")
    id("love.forte.plugin.suspend-transform")
}

group = "id.walt.crypto"
suspendTransformPlugin {
    enabled = true
    includeRuntime = true
    transformers { useDefault() }
}



kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_1_8
                }
        }
    }
}

android {
    namespace = "id.walt.crypto"
    compileSdk = 35
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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
                implementation("io.github.oshai:kotlin-logging:7.0.5")
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
