plugins {
    id("com.android.test")
}

val javaVersion = JavaVersion.toVersion(identityLibs.versions.java.library.get())
val targetAppId = "id.walt.walletdemo.compose"

android {
    namespace = "id.walt.walletdemo.compose.e2e"
    compileSdk = 37
    targetProjectPath = ":waltid-applications:waltid-wallet-demo-compose:androidApp"
    // The runner must survive termination of the wallet app.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 30
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["targetAppId"] = targetAppId
    }
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
    }
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    sourceSets["main"].kotlin.directories.add("../androidTestFixtures/kotlin")
}

dependencies {
    implementation(identityLibs.androidx.test.ext.junit)
    implementation(identityLibs.androidx.test.runner)
    implementation(identityLibs.androidx.test.uiautomator)
    implementation(identityLibs.ktor.client.android)
    implementation(project(":waltid-libraries:protocols:waltid-mobile-test-utils"))
}
