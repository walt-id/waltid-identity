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
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("io.github.optimumcode:json-schema-validator:0.4.0")
            implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // Kotlinx
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // walt.id
            api(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            api(project(":waltid-libraries:credentials:waltid-dcql"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmMain.dependencies {
            // Ktor client
            // implementation("io.ktor:ktor-client-okhttp:$ktor_version")
        }
        jvmTest.dependencies {
            implementation("org.slf4j:slf4j-simple:2.0.17")
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id Holder Policies")
        description.set("walt.id Kotlin/Java library for Holder Policies")
    }
}
