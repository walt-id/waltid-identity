import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
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

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}


repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.walt.id/repository/waltid/")
    maven("https://maven.walt.id/repository/waltid-ssi-kit/")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

/*java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}*/

kotlin {
    jvmToolchain(17)
}

dependencies {
    // nftkit
    implementation("id.walt:waltid-nftkit:1.2311291144.0") {
        exclude("com.sksamuel.hoplite", "hoplite-core")
        exclude("com.sksamuel.hoplite", "hoplite-yaml")
        exclude("com.sksamuel.hoplite", "hoplite-hikaricp")
    }

    /* -- KTOR -- */

    val ktorVersion = "2.3.8"
    // Ktor server
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auto-head-response-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-double-receive-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-host-common-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-compression-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-forwarded-header-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-method-override:$ktorVersion")

    // Ktor server external libs
    implementation("io.github.smiley4:ktor-swagger-ui:2.7.4")
    //implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")

    // Ktor client
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")

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
    implementation("org.bouncycastle:bcprov-lts8on:2.73.4")

    // Argon2
    implementation("de.mkammerer:argon2-jvm:2.11")


    // waltid-did
    implementation(project(":waltid-crypto"))
    implementation(project(":waltid-did"))

    // OIDC
    implementation(project(":waltid-openid4vc"))
    implementation(project(":waltid-sdjwt"))
    
    testImplementation(project(":waltid-issuer-api"))
    testImplementation(project(":waltid-verifier-api"))
    
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    implementation("io.ktor:ktor-client-java:$ktorVersion")

    /* -- Misc --*/

    // Cache
    implementation("io.github.reactivecircus.cache4k:cache4k:0.13.0")

    // Webauthn
     implementation("com.webauthn4j:webauthn4j-core:0.22.2.RELEASE") {
         exclude("ch.qos.logback")
     }

    // DB
    implementation("org.jetbrains.exposed:exposed-core:0.47.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.47.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.47.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.47.0")
    implementation("org.jetbrains.exposed:exposed-json:0.47.0")
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
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")
    implementation("org.slf4j:slf4j-simple:2.0.12")
    implementation("org.slf4j:jul-to-slf4j:2.0.12")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("io.mockk:mockk:1.13.10")
}
