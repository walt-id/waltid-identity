import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version "2.3.8"
    kotlin("plugin.serialization")

    id("com.github.ben-manes.versions") version "0.49.0"
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
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<org.gradle.api.tasks.bundling.Zip> {
    isZip64 = true
}

/*java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}*/

kotlin {
    jvmToolchain(17)
}

dependencies {


    /* -- KTOR -- */

    val ktor_version = "2.3.11"
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

    // Ktor server external libs
    implementation("io.github.smiley4:ktor-swagger-ui:2.8.0")
    //implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")

    // Ktor client
    implementation("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-client-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")

    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Date
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // UUID
    implementation("app.softwork:kotlinx-uuid-core:0.0.22")
    implementation("app.softwork:kotlinx-uuid-exposed:0.0.22")

    /* -- Security -- */
    // Bouncy Castle
    implementation("org.bouncycastle:bcprov-lts8on:2.73.6")

    // Argon2
    implementation("de.mkammerer:argon2-jvm:2.11")


    // OIDC
    implementation(project(":waltid-openid4vc"))
    implementation(project(":waltid-sdjwt"))

    implementation(project(":waltid-crypto"))
    implementation(project(":waltid-crypto-oci"))
    implementation(project(":waltid-did"))

    testImplementation(project(":waltid-issuer-api"))
    testImplementation(project(":waltid-verifier-api"))

    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")

    implementation("io.ktor:ktor-client-java:$ktor_version")

    /* -- Misc --*/

    // Cache
    implementation("io.github.reactivecircus.cache4k:cache4k:0.13.0")

    // Webauthn
    implementation("com.webauthn4j:webauthn4j-core:0.22.1.RELEASE") {
        exclude("ch.qos.logback")
    }

    // DB
    implementation("org.jetbrains.exposed:exposed-core:0.49.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.47.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.49.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.48.0")
    implementation("org.jetbrains.exposed:exposed-json:0.50.1")
    // drivers
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("mysql:mysql-connector-java:8.0.33")
    implementation("com.microsoft.sqlserver:mssql-jdbc:9.4.1.jre8")
    // migration
    //implementation("org.flywaydb:flyway-core:9.22.2")

    // Web push
    // implementation("dev.blanke.webpush:webpush:6.1.1") // alternative
    implementation("com.interaso:webpush:1.1.1")

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:2.8.0.RC3")
    implementation("com.sksamuel.hoplite:hoplite-hocon:2.8.0.RC3")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.8.0.RC3")
    implementation("com.sksamuel.hoplite:hoplite-hikaricp:2.8.0.RC3")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("org.slf4j:jul-to-slf4j:2.0.12")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.23")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("io.mockk:mockk:1.13.10")
}
