@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.holderpolicies"

kotlin {
    js(IR) {
        outputModuleName.set("holder-policies")
    }

    sourceSets {
        commonMain.dependencies {
            // JSON
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.optimumcode.jsonschemavalidator)
            implementation(identityLibs.jsonpathkt)

            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // Kotlinx
            implementation(identityLibs.kotlinx.datetime)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // walt.id
            api(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            api(project(":waltid-libraries:credentials:waltid-dcql"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            // Ktor client
            // implementation("io.ktor:ktor-client-okhttp:$ktor_version")
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Holder Policies")
        description.set("walt.id Kotlin/Java library for Holder Policies")
    }
}
