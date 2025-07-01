import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("jvm")
    kotlin("plugin.power-assert")
    kotlin("plugin.serialization")
    id("io.ktor.plugin") version "3.2.0"
    id("maven-publish")

    application

    id("com.github.ben-manes.versions")
}

group = "id.walt"

application {
    mainClass.set("id.walt.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://maven.waltid.dev/releases")
}

dependencies {
    // Auth methods
    // Core Web3j library
    implementation("org.web3j:core:4.12.3") // (5.0.0 is an invalid version!)

    // Optional: Web3j utils
    implementation("org.web3j:utils:4.12.3") // (5.0.0 is an invalid version!)


    // RADIUS
    implementation("org.aaa4j.radius:aaa4j-radius-client:0.3.1")

    // LDAP
    implementation("org.apache.directory.api:apache-ldap-api:2.1.7") {
        exclude("org.apache.mina:mina-core") // Manually updated due to security CVE
    }
    implementation("org.apache.mina:mina-core:2.2.4")

    // TOTP/HOTP
    implementation("com.atlassian:onetime:2.1.2")

    // JWT
    implementation(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation("com.nimbusds:nimbus-jose-jwt:9.41.2")

    // Cryptography
    /*implementation(platform("dev.whyoleg.cryptography:cryptography-bom:0.4.0"))
    implementation("dev.whyoleg.cryptography:cryptography-core")
    implementation("dev.whyoleg.cryptography:cryptography-provider-jdk")*/
    implementation("com.password4j:password4j:1.8.2")

    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-auth-jwt-jvm")
    //implementation("io.ktor:ktor-server-auth-ldap-jvm")
    implementation("io.ktor:ktor-client-core-jvm")
    implementation("io.ktor:ktor-client-apache-jvm")
    implementation("io.ktor:ktor-server-sessions-jvm")
    implementation("io.ktor:ktor-server-auto-head-response-jvm")
    implementation("io.ktor:ktor-server-double-receive-jvm")
    implementation("io.ktor:ktor-server-webjars-jvm")
    implementation("io.ktor:ktor-server-host-common-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-default-headers-jvm")
    implementation("io.ktor:ktor-server-forwarded-header-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-server-cio-jvm")

    // Ktor client
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")

    // Ktor shared
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")

    // Ktor server external
    implementation("io.github.smiley4:ktor-openapi:5.0.2")
    implementation("io.github.smiley4:ktor-swagger-ui:5.0.2")
    implementation("io.github.smiley4:ktor-redoc:5.0.2")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("com.eygraber:jsonpathkt-kotlinx:3.0.2")

    // Logging
    implementation("io.klogging:klogging-jvm:0.9.4")
    implementation("io.klogging:slf4j-klogging:0.9.4")

    /* --- Testing --- */
    testImplementation("io.ktor:ktor-client-logging")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")

    // Ktor
    testImplementation("io.ktor:ktor-server-cio-jvm")
    testImplementation("io.ktor:ktor-server-test-host")

    // Kotlin
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id ktor-authnz")
                description.set(
                    """
                    Kotlin/Java library for AuthNZ
                    """.trimIndent()
                )
                url.set("https://walt.id")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("walt.id")
                        name.set("walt.id")
                        email.set("office@walt.id")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(if (version.toString().endsWith("SNAPSHOT")) uri("https://maven.waltid.dev/snapshots") else uri("https://maven.waltid.dev/releases"))
            credentials {
                username = System.getenv("MAVEN_USERNAME") ?: File("$rootDir/secret_maven_username.txt").let { if (it.isFile) it.readLines().first() else "" }
                password = System.getenv("MAVEN_PASSWORD") ?: File("$rootDir/secret_maven_password.txt").let { if (it.isFile) it.readLines().first() else "" }
            }
        }
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    includedSourceSets = listOf("test")
    functions = listOf(
        // kotlin.test
        "kotlin.assert", "kotlin.test.assertTrue", "kotlin.test.assertEquals", "kotlin.test.assertNull",

        // checks
        "kotlin.require", "kotlin.check"
    )
}
