import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("waltid.multiplatform.library")

    id("com.android.library")
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

// 2. Configure the Android Extension
android {
    namespace = project.group.toString()

    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaVersion)
        targetCompatibility = JavaVersion.toVersion(javaVersion)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
