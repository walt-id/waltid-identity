import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// For new mobile-only modules, prefer waltid.mobile.library instead.
plugins {
    id("waltid.android.base")
}

kotlin {
    androidLibrary {
        namespace = project.group.toString()
        compileSdk = WaltidBuildConstants.COMPILE_SDK
        minSdk = WaltidBuildConstants.MIN_SDK

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
                excludes += WaltidBuildConstants.META_INF_EXCLUDES
            }
        }
    }
}
