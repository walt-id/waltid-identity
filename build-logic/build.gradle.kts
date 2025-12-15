plugins {
    `kotlin-dsl`
}

dependencies {
    // Share version catalog with build logic
    implementation(files(identityLibs.javaClass.superclass.protectionDomain.codeSource.location))

    // --- Plugins here: ---

    // Kotlin
    implementation(identityLibs.kotlin.gradle.plugin)

    // Serialization
    implementation(identityLibs.kotlin.plugin.serialization)

    // Ktor
    implementation(identityLibs.ktor.gradle.plugin)
    implementation(identityLibs.jib.gradle.plugin)

    // Publishing
    implementation(identityLibs.vanniktech.publish.plugin)
    implementation(identityLibs.petuska.npm.publish.plugin)

    // Suspend-transform
    implementation(identityLibs.loveforte.suspendtransform)

    // Power-assert
    implementation(identityLibs.powerassert)

    // Discover dependency updates
    implementation(identityLibs.benmanes.versions)
    //implementation(identityLibs.owasp.dependencycheck)

    // Android
    implementation(identityLibs.android.gradle.plugin)
    implementation(identityLibs.jetbrains.compose)
}
