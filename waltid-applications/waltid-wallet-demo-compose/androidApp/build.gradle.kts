import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("plugin.compose")
    alias(identityLibs.plugins.google.services)
}

val javaVersion = identityLibs.versions.java.library.get().toInt()
val publicDemoTransactionDataProfilesUrl = "https://wallet.demo.walt.id/wallet-api/transaction-data-profiles"
val walletSigningProtectionMode =
    providers.gradleProperty("walletSigningProtectionMode").getOrElse("optional").trim().lowercase()
require(walletSigningProtectionMode in setOf("required", "optional", "disabled")) {
    "walletSigningProtectionMode must be required, optional, or disabled"
}

val appVersionName: String = providers.gradleProperty("appVersionName").orNull?.takeIf { it.isNotBlank() } ?: "0.1.0"
val appVersionCode: Int = run {
    val override = providers.gradleProperty("appVersionCode").orNull?.toIntOrNull()
    if (override != null) {
        require(override > 0) { "appVersionCode must be a positive integer" }
        return@run override
    }
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

        buildConfigField("String", "ATTESTATION_BASE_URL", "\"${providers.gradleProperty("attestation.baseUrl").getOrElse("")}\"")
        buildConfigField("String", "ATTESTATION_ATTESTER_PATH", "\"${providers.gradleProperty("attestation.attesterPath").getOrElse("")}\"")
        buildConfigField("String", "ATTESTATION_BEARER_TOKEN", "\"${providers.gradleProperty("attestation.bearerToken").getOrElse("")}\"")
        buildConfigField("String", "ATTESTATION_HOST_HEADER", "\"${providers.gradleProperty("attestation.hostHeader").getOrElse("")}\"")
        buildConfigField("String", "TRANSACTION_DATA_PROFILES_URL", "\"${providers.gradleProperty("transactionDataProfiles.url").getOrElse(publicDemoTransactionDataProfilesUrl)}\"")
        buildConfigField("String", "WALLET_SIGNING_PROTECTION_MODE", "\"$walletSigningProtectionMode\"")
    }

    flavorDimensions += "environment"
    productFlavors {
        create("production") {
            dimension = "environment"
            isDefault = true
        }
        create("preview") {
            dimension = "environment"
            applicationId = "id.walt.wallet.compose.test"
        }
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
            merges += "META-INF/LICENSE.md"
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
    sourceSets["androidTest"].kotlin.directories.add("../androidTestFixtures/kotlin")
}

dependencies {
    implementation(project(":waltid-applications:waltid-wallet-demo-compose:sharedLogic"))
    implementation(project(":waltid-applications:waltid-wallet-demo-compose:sharedUI"))
    implementation(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile"))
    implementation(identityLibs.androidx.activity.compose)
    implementation(identityLibs.androidx.credentials.registry.provider)
    debugImplementation(identityLibs.androidx.credentials.play.services.auth)
    debugImplementation(identityLibs.androidx.lifecycle.runtime)
    implementation(identityLibs.kotlinx.coroutines.android)
    implementation(identityLibs.kotlinx.serialization.json)
    implementation(identityLibs.androidx.fragment)
    implementation(platform(identityLibs.firebase.bom))

    testImplementation(kotlin("test"))
    testImplementation(identityLibs.junit)
    testImplementation(identityLibs.robolectric)
    testImplementation(identityLibs.kotlinx.coroutines.test)

    androidTestImplementation(identityLibs.androidx.test.ext.junit)
    androidTestImplementation(identityLibs.androidx.test.runner)
    androidTestImplementation(identityLibs.coil.compose)
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

googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.IGNORE
}
