import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version "2.3.12"
    kotlin("plugin.serialization")

    id("com.github.ben-manes.versions")
}

group = "id.walt"
application {
    mainClass.set("id.walt.webwallet.MainKt")
    applicationName = "waltid-wallet-api"
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.waltid.dev/releases")
    maven("https://maven.walt.id/repository/waltid-ssi-kit/")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<Zip> {
    isZip64 = true
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

/*java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}*/

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":waltid-services:waltid-service-commons"))

    /* -- KTOR -- */

    val ktor_version = "2.3.12"
    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-auto-head-response-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-double-receive-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-host-common-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-compression-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-cors-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-call-id-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-method-override:$ktor_version")
    implementation("io.ktor:ktor-server-rate-limit:$ktor_version")

    // Ktor client
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-client-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")

    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    // Date
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // UUID
    implementation("app.softwork:kotlinx-uuid-core:0.0.26")
    implementation("app.softwork:kotlinx-uuid-exposed:0.0.26")

    /* -- Security -- */
    // Bouncy Castle
    implementation("org.bouncycastle:bcprov-lts8on:2.73.6")

    // Argon2
    implementation("de.mkammerer:argon2-jvm:2.11")


    // OIDC
    implementation(project(":waltid-libraries:waltid-openid4vc"))
    implementation(project(":waltid-libraries:waltid-sdjwt"))
    implementation(project(":waltid-libraries:waltid-mdoc-credentials"))

    implementation(project(":waltid-libraries:waltid-crypto"))
    implementation(project(":waltid-libraries:waltid-crypto-oci"))
    implementation(project(":waltid-libraries:waltid-did"))

    testImplementation(project(":waltid-services:waltid-issuer-api"))
    testImplementation(project(":waltid-services:waltid-verifier-api"))

    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
    implementation("com.augustcellars.cose:cose-java:1.1.0")

    implementation("io.ktor:ktor-client-java:$ktor_version")

    /* -- Misc --*/

    // Cache
    implementation("io.github.reactivecircus.cache4k:cache4k:0.13.0")

    // Webauthn
    implementation("com.webauthn4j:webauthn4j-core:0.25.0.RELEASE") {
        exclude("ch.qos.logback")
    }

    // DB
    implementation("org.jetbrains.exposed:exposed-core:0.52.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.52.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.52.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.52.0")
    implementation("org.jetbrains.exposed:exposed-json:0.52.0")
    // drivers
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.7.0.jre8-preview")

    // Web push
    // implementation("dev.blanke.webpush:webpush:6.1.1") // alternative
    implementation("com.interaso:webpush:1.2.0")

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:2.8.0.RC3")
    implementation("com.sksamuel.hoplite:hoplite-hocon:2.8.0.RC3")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.8.0.RC3")
    implementation("com.sksamuel.hoplite:hoplite-hikaricp:2.8.0.RC3")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
    implementation("org.slf4j:jul-to-slf4j:2.0.13")
    implementation("io.klogging:klogging-jvm:0.7.0")
    implementation("io.klogging:slf4j-klogging:0.7.0")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.0")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("io.klogging:klogging-jvm:0.6.1")
}
