import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// For new mobile-only modules, prefer waltid.mobile.library instead.
plugins {
    id("waltid.android.base")
}

kotlin {
    android {
        namespace = project.group.toString()
        compileSdk = BuildConstants.COMPILE_SDK
        minSdk = BuildConstants.MIN_SDK

        withJava()

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
}
