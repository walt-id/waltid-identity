import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

val javaVersion = identityLibs.versions.java.library.get().toInt()

android {
    namespace = "id.walt.walletdemo"
    compileSdk = 37

    defaultConfig {
        applicationId = "id.walt.walletdemo"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ATTESTATION_BASE_URL", "\"${findProperty("attestation.baseUrl") ?: ""}\"")
        buildConfigField("String", "ATTESTATION_ATTESTER_PATH", "\"${findProperty("attestation.attesterPath") ?: ""}\"")
        buildConfigField("String", "ATTESTATION_BEARER_TOKEN", "\"${findProperty("attestation.bearerToken") ?: ""}\"")
        buildConfigField("String", "ATTESTATION_HOST_HEADER", "\"${findProperty("attestation.hostHeader") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaVersion)
        targetCompatibility = JavaVersion.toVersion(javaVersion)
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
}

dependencies {
    implementation(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-client"))

    implementation(identityLibs.ktor.client.android)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.serialization.kotlinx.json)

    implementation(identityLibs.androidx.core.ktx)
    implementation(identityLibs.androidx.lifecycle.runtime.ktx)
    implementation(identityLibs.androidx.lifecycle.viewmodel.compose)
    implementation(identityLibs.androidx.activity.compose)

    implementation(platform(identityLibs.androidx.compose.bom))
    implementation(identityLibs.androidx.compose.ui)
    implementation(identityLibs.androidx.compose.ui.tooling.preview)
    implementation(identityLibs.androidx.compose.material3)

    debugImplementation(identityLibs.androidx.compose.ui.tooling)

    androidTestImplementation(identityLibs.androidx.test.ext.junit)
    androidTestImplementation(identityLibs.androidx.test.runner)
    androidTestImplementation(identityLibs.androidx.test.uiautomator)
    androidTestImplementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
}
