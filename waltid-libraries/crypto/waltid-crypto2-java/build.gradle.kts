@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("waltid.multiplatform.library.jvm")
    id("waltid.publish.maven")
}

group = "id.walt.crypto2"

kotlin {
    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }

    sourceSets {
        jvmMain.dependencies {
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            api(identityLibs.jspecify)
            implementation(identityLibs.kotlinx.coroutines.jdk8)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id crypto2 Java compatibility")
        description.set("Java provider SPI and adapters for walt.id crypto2")
    }
}
