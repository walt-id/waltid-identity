import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

val javaVersion = identityLibs.versions.java.library.get().toInt()
val publicDemoTransactionDataProfilesUrl = "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles"

android {
    namespace = "id.walt.walletdemo.compose.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "id.walt.walletdemo.compose"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ATTESTATION_BASE_URL", "\"${findProperty("attestation.baseUrl") ?: ""}\"")
        buildConfigField("String", "ATTESTATION_ATTESTER_PATH", "\"${findProperty("attestation.attesterPath") ?: ""}\"")
        buildConfigField("String", "ATTESTATION_BEARER_TOKEN", "\"${findProperty("attestation.bearerToken") ?: ""}\"")
        buildConfigField("String", "ATTESTATION_HOST_HEADER", "\"${findProperty("attestation.hostHeader") ?: ""}\"")
        buildConfigField("String", "TRANSACTION_DATA_PROFILES_URL", "\"${findProperty("transactionDataProfiles.url") ?: publicDemoTransactionDataProfilesUrl}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaVersion)
        targetCompatibility = JavaVersion.toVersion(javaVersion)
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
    implementation(project(":waltid-applications:waltid-wallet-demo-compose:sharedLogic"))
    implementation(project(":waltid-applications:waltid-wallet-demo-compose:sharedUI"))
    implementation(identityLibs.androidx.activity.compose)

    androidTestImplementation(identityLibs.androidx.test.ext.junit)
    androidTestImplementation(identityLibs.androidx.test.runner)
    androidTestImplementation(identityLibs.androidx.test.uiautomator)
    androidTestImplementation(identityLibs.ktor.client.android)
    androidTestImplementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
}
