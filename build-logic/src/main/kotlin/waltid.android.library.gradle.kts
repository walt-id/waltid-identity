import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.android.build.api.dsl.androidLibrary

plugins {
    id("waltid.android.base")
    id("com.android.kotlin.multiplatform.library")
}

// Access the version catalog
val catalogs = extensions.getByType<VersionCatalogsExtension>()
val identityLibs = catalogs.named("identityLibs")
val javaVersion = identityLibs.findVersion("java-library").get().requiredVersion.toInt()

kotlin {
    androidLibrary {
        namespace = project.group.toString()
        compileSdk = 36
        minSdk = 30

        withJava()

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
                }
            }
        }

        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
    }
}
