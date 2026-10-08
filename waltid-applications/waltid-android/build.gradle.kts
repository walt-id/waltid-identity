plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "id.walt.androidSample"
    compileSdk = 37

    defaultConfig {
        applicationId = "id.walt.androidSample"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/DEPENDENCIES"
            merges += "META-INF/LICENSE.md"
        }
    }
}

dependencies {
    // walt.id
    api(project(":waltid-libraries:crypto:waltid-crypto"))
    api(project(":waltid-libraries:waltid-did"))
    api(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
    api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))

    // JSON
    implementation(identityLibs.kotlinx.serialization.json)

    // -- Android --
    implementation(identityLibs.androidx.biometric.preview)
    implementation(identityLibs.androidx.navigation.compose)
    implementation(identityLibs.androidx.core)
    implementation(identityLibs.androidx.lifecycle.runtime)
    implementation(identityLibs.androidx.activity.compose)

    // Compose
    implementation(platform(identityLibs.androidx.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation(identityLibs.androidx.lifecycle.runtime.compose)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
    testImplementation(identityLibs.junit)
    testImplementation(identityLibs.androidx.test.ext.junit)
    testImplementation(identityLibs.androidx.test.espresso)
    androidTestImplementation(identityLibs.androidx.test.ext.junit)
    androidTestImplementation(identityLibs.androidx.test.espresso)
    androidTestImplementation(platform(identityLibs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
