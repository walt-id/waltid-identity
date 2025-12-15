import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("waltid.multiplatform.library")
}

// Access the version catalog
val catalogs = extensions.getByType<VersionCatalogsExtension>()
val libs = catalogs.named("identityLibs")
val javaVersion = libs.findVersion("java-library").get().requiredVersion.toInt()

// Configure KMP to have an Android Target
kotlin {
    androidTarget {
        publishLibraryVariants("release")

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
                }
            }
        }
    }
}
