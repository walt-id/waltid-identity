import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("waltid.multiplatform.library")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(project.javaLibraryVersion.toString()))
                }
            }
        }
    }
}
