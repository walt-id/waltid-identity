import org.jetbrains.kotlin.gradle.dsl.JvmTarget

private val nonNamespaceSegmentCharacter = Regex("[^A-Za-z0-9_]")

fun String.toNamespaceSegments() = split(".").mapNotNull { segment ->
    val sanitized = segment.replace(nonNamespaceSegmentCharacter, "_")
    when {
        sanitized.isBlank() -> null
        sanitized.first().isDigit() -> "_$sanitized"
        else -> sanitized
    }
}

fun Project.defaultAndroidNamespace(): String {
    // Examples:
    // :waltid-libraries:crypto:waltid-crypto -> id.walt.crypto
    // :waltid-libraries:protocols:waltid-openid4vc-wallet -> id.walt.protocols.openid4vc.wallet
    val pathSegments = path.split(":")
        .filter { it.isNotBlank() }
        .dropWhile { it == "waltid-libraries" }
        .flatMap { it.removePrefix("waltid-").replace('-', '.').toNamespaceSegments() }

    val uniquePathSegments = pathSegments.fold(emptyList<String>()) { segments, segment ->
        if (segments.lastOrNull() == segment) {
            segments
        } else {
            segments + segment
        }
    }

    return (listOf("id", "walt") + uniquePathSegments).joinToString(".")
}

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = project.defaultAndroidNamespace()
        compileSdk = BuildConstants.COMPILE_SDK
        minSdk = BuildConstants.MIN_SDK

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
        withHostTestBuilder { }

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(project.javaLibraryVersion.toString()))
                }
            }
        }

        packaging {
            resources {
                excludes += BuildConstants.META_INF_EXCLUDES
            }
        }
    }

    sourceSets {
        androidMain.get().dependsOn(named("jvmAndroidMain").get())
    }
}
