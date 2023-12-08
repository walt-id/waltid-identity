import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.21"
    id("io.ktor.plugin") version "2.3.7"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.21"

    id("com.github.ben-manes.versions") version "0.48.0"
}

group = "id.walt"
version = "0.0.1"
application {
    mainClass.set("id.walt.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}


repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://maven.walt.id/repository/waltid/")
    maven("https://maven.walt.id/repository/waltid-ssi-kit/")
    //maven("https://repo.danubetech.com/repository/maven-public/")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "19"
}

/*java {
    sourceCompatibility = JavaVersion.VERSION_15
    targetCompatibility = JavaVersion.VERSION_15
}*/

kotlin {
    jvmToolchain(19)
}

dependencies {
    // nftkit
    implementation("id.walt:waltid-nftkit:1.2311291144.0") {
        exclude("com.sksamuel.hoplite", "hoplite-core")
        exclude("com.sksamuel.hoplite", "hoplite-yaml")
        exclude("com.sksamuel.hoplite", "hoplite-hikaricp")
    }

    /* -- KTOR -- */

    val ktorVersion = "2.3.7"
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
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")

    // Ktor server external libs
    implementation("io.github.smiley4:ktor-swagger-ui:2.7.1")
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
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Date
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // UUID
    implementation("app.softwork:kotlinx-uuid-core:0.0.22")
    implementation("app.softwork:kotlinx-uuid-exposed:0.0.22")

    /* -- Security -- */
    // Bouncy Castle
    implementation("org.bouncycastle:bcprov-jdk18on:1.76")

    // Argon2
    implementation("de.mkammerer:argon2-jvm:2.11")


    // waltid-did
    implementation("id.walt.did:waltid-did:1.1.1")//id.walt.crypto provided by id.walt.did:waltid-did

    // OIDC
    implementation("id.walt:waltid-openid4vc:1.2310051536.0")
    //implementation("id.walt:waltid-openid4vc:1.2311161107.0")

    /* -- Misc --*/

    // Cache
    implementation("io.github.reactivecircus.cache4k:cache4k:0.12.0")

    // DB
    implementation("org.jetbrains.exposed:exposed-core:0.45.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.45.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.45.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.45.0")
    implementation("org.jetbrains.exposed:exposed-json:0.45.0")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    implementation("org.postgresql:postgresql:42.7.1")
    // migration
    //implementation("org.flywaydb:flyway-core:9.22.2")

    // Web push
    implementation("nl.martijndwars:web-push:5.1.1") // todo: replace with https://github.com/interaso/webpush

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:2.8.0.RC3")
    implementation("com.sksamuel.hoplite:hoplite-hocon:2.8.0.RC3")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.8.0.RC3")
    implementation("com.sksamuel.hoplite:hoplite-hikaricp:2.8.0.RC3")
    implementation("com.zaxxer:HikariCP:5.0.1")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.1")
    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("org.slf4j:jul-to-slf4j:2.0.9")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.21")

    /*testImplementation("io.kotest:kotest-runner-junit5:5.5.5")
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
    testImplementation("io.kotest.extensions:kotest-assertions-ktor:2.0.0")*/
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
}
