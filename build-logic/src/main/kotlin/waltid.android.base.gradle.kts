import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.android.build.api.dsl.androidLibrary

plugins {
    id("waltid.multiplatform.library")
}

// Access the version catalog
val catalogs = extensions.getByType<VersionCatalogsExtension>()
val identityLibs = catalogs.named("identityLibs")
val javaVersion = identityLibs.findVersion("java-library").get().requiredVersion.toInt()

// Configure KMP to have an Android Target
kotlin {
    androidLibrary {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
                }
            }
        }
    }
}
