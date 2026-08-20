import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

val javaVersion = identityLibs.versions.java.library.get().toInt()
val publicDemoTransactionDataProfilesUrl = "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles"
val walletBiometricEnabled = when ((findProperty("walletBiometricEnabled") as String?)?.trim()?.lowercase()) {
    "0", "false", "no", "off" -> false
    else -> true
}

val appVersionName: String = (findProperty("appVersionName") as String?)?.takeIf { it.isNotBlank() } ?: "0.1.0"
val appVersionCode: Int = run {
    val core = appVersionName.trimStart('v', 'V').substringBefore('-').substringBefore('+')
    val parts = core.split('.')
    fun slot(i: Int) = (parts.getOrNull(i)?.toIntOrNull() ?: 0).coerceIn(0, 999)
    (slot(0) * 1_000_000L + slot(1) * 1_000L + slot(2)).coerceIn(1L, 2_100_000_000L).toInt()
}

android {
    namespace = "id.walt.walletdemo.compose.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "id.walt.walletdemo.compose"
        minSdk = 30
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ATTESTATION_BASE_URL", "\"${findProperty("attestation.baseUrl") ?: ""}\"")
        buildConfigField("String", "ATTESTATION_ATTESTER_PATH", "\"${findProperty("attestation.attesterPath") ?: ""}\"")
        buildConfigField("String", "ATTESTATION_BEARER_TOKEN", "\"${findProperty("attestation.bearerToken") ?: ""}\"")
        buildConfigField("String", "ATTESTATION_HOST_HEADER", "\"${findProperty("attestation.hostHeader") ?: ""}\"")
        buildConfigField("String", "TRANSACTION_DATA_PROFILES_URL", "\"${findProperty("transactionDataProfiles.url") ?: publicDemoTransactionDataProfilesUrl}\"")
        buildConfigField("boolean", "WALLET_BIOMETRIC_ENABLED", walletBiometricEnabled.toString())
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":waltid-applications:waltid-wallet-demo-compose:sharedLogic"))
    implementation(project(":waltid-applications:waltid-wallet-demo-compose:sharedUI"))
    implementation(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile"))
    implementation(identityLibs.androidx.activity.compose)
    implementation(identityLibs.androidx.credentials.registry.provider)
    debugImplementation(identityLibs.androidx.credentials.play.services.auth)
    debugImplementation(identityLibs.androidx.lifecycle.runtime.ktx)
    implementation(identityLibs.kotlinx.coroutines.android)
    implementation(identityLibs.kotlinx.serialization.json)
    implementation(identityLibs.androidx.fragment)

    testImplementation(kotlin("test"))
    testImplementation(identityLibs.junit)
    testImplementation(identityLibs.robolectric)

    androidTestImplementation(identityLibs.androidx.test.ext.junit)
    androidTestImplementation(identityLibs.androidx.test.runner)
    androidTestImplementation(identityLibs.androidx.test.uiautomator)
    androidTestImplementation(identityLibs.ktor.client.android)
    androidTestImplementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
    // The Annex C E2E is its own reader, because Annex C has no back-channel: the encrypted
    // DeviceResponse returns through the OS to whoever called getCredential. It builds the request
    // and decrypts the response with the same shared code the deployed verifier runs.
    androidTestImplementation(project(":waltid-libraries:protocols:waltid-18013-7-verifier"))
    androidTestImplementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
    androidTestImplementation(project(":waltid-libraries:crypto:waltid-crypto2"))
    androidTestImplementation(project(":waltid-libraries:crypto:waltid-cose"))
}
