import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

object Versions {
    const val KTOR_VERSION = "2.3.12" // also change 1 plugin
    const val COROUTINES_VERSION = "1.8.1"
    const val EXPOSED_VERSION = "0.43.0"
    const val HOPLITE_VERSION = "2.8.0.RC3"
}

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")

    id("io.ktor.plugin") version "2.3.12" // Versions.KTOR_VERSION
    id("org.owasp.dependencycheck") version "9.2.0"
    id("com.github.jk1.dependency-license-report") version "2.8"
    id("com.github.ben-manes.versions")
    application
}

group = "id.walt"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.waltid.dev/releases")
}


dependencies {
    api(project(":waltid-services:waltid-service-commons"))

    /* -- KTOR -- */

    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auth-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-sessions-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-auto-head-response-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-double-receive-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-host-common-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-status-pages-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-compression-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-cors-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-call-logging-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-call-id-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-server-cio-jvm:${Versions.KTOR_VERSION}")

    // Ktor client
    implementation("io.ktor:ktor-client-core-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-serialization-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-json-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-logging-jvm:${Versions.KTOR_VERSION}")


    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:${Versions.KTOR_VERSION}")

    // Date
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES_VERSION}")

    // UUID
    implementation("app.softwork:kotlinx-uuid-core:0.0.26")

    /* -- Misc --*/

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    implementation("org.slf4j:jul-to-slf4j:2.0.13")
    implementation("io.klogging:klogging-jvm:0.7.0")
    implementation("io.klogging:slf4j-klogging:0.7.0")

    implementation("io.ktor:ktor-client-okhttp-jvm:${Versions.KTOR_VERSION}")

    // Crypto
    implementation("com.augustcellars.cose:cose-java:1.1.0")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")

    // Test
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES_VERSION}")

    // OIDC
    api(project(":waltid-libraries:protocols:waltid-openid4vc"))

    // SSI Kit 2
    api(project(":waltid-libraries:crypto:waltid-crypto"))
    api(project(":waltid-libraries:credentials:waltid-verifiable-credentials"))
    api(project(":waltid-libraries:credentials:waltid-verification-policies"))
    api(project(":waltid-libraries:waltid-did"))
    api(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
}
tasks.withType<Zip> {
    isZip64 = true
}
tasks.withType<Test> {
    useJUnitPlatform()

    // Use the following condition to optionally run the integration tests:
    // > gradle build -PrunIntegrationTests
    if (!project.hasProperty("runIntegrationTests")) {
        exclude("id/walt/test/integration/**")
    }
}

tasks.withType<ProcessResources> {
    doLast {
        layout.buildDirectory.get().file("resources/main/version.properties").asFile.run {
            parentFile.mkdirs()
            Properties().run {
                setProperty("version", rootProject.version.toString())
                writer().use { store(it, "walt.id version store") }
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        windowsScript.writeText(
            windowsScript.readText().replace(Regex("set CLASSPATH=.*"), "set CLASSPATH=%APP_HOME%\\\\lib\\\\*")
        )
    }
}

application {
    mainClass.set("id.walt.verifier.MainKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

//licenseReport {
//    renderers = arrayOf<ReportRenderer>(InventoryHtmlReportRenderer("waltid-verifier-licenses-report.html", "walt.id verifier"))
//    filters = arrayOf<DependencyFilter>(LicenseBundleNormalizer())
//}
