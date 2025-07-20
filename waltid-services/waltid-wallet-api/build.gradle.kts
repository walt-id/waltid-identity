import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    kotlin("jvm")
    id("io.ktor.plugin") version "3.2.0"
    kotlin("plugin.serialization")
    id("maven-publish")
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
    maven("https://maven.waltid.dev/snapshots")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
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
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(project(":waltid-services:waltid-service-commons"))

    /* -- KTOR -- */

    val ktor_version = "3.1.2"
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
    implementation("io.ktor:ktor-client-serialization:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-client-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-logging-jvm:$ktor_version")

    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Date
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // Uuid
    implementation("app.softwork:kotlinx-uuid-core:0.1.4")
    //implementation("app.softwork:kotlinx-uuid-exposed:0.1.2")

    /* -- Security -- */
    // Bouncy Castle
    implementation("org.bouncycastle:bcprov-lts8on:2.73.7")
    implementation("org.bouncycastle:bcpkix-lts8on:2.73.7")

    // Argon2
    implementation("de.mkammerer:argon2-jvm:2.11")


    // walt.id
    implementation(project(":waltid-libraries:protocols:waltid-openid4vc"))
    implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
    implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
    implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))

    implementation(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-oci"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-aws"))


    implementation(project(":waltid-libraries:waltid-did"))
    implementation(project(":waltid-libraries:credentials:waltid-verification-policies"))
    implementation(project(":waltid-libraries:credentials:waltid-dif-definitions-parser"))

    implementation(project(":waltid-libraries:auth:waltid-ktor-authnz"))

    implementation(project(":waltid-libraries:waltid-core-wallet"))

    testImplementation(project(":waltid-services:waltid-issuer-api"))
    testImplementation(project(":waltid-services:waltid-verifier-api"))

    implementation("com.nimbusds:nimbus-jose-jwt:10.0.1")
    implementation("org.cose:cose-java:1.1.1-WALT-SNAPSHOT")

    implementation("io.ktor:ktor-client-java:$ktor_version")

    /* -- Misc --*/

    // Cache
    implementation("io.github.reactivecircus.cache4k:cache4k:0.14.0")

    // Webauthn
    /*implementation("com.webauthn4j:webauthn4j-core:0.28.5.RELEASE") {
        exclude("ch.qos.logback")
    }*/ // Not implemented right now

    // DB
    implementation("org.jetbrains.exposed:exposed-core:0.59.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.59.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.59.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.59.0")
    implementation("org.jetbrains.exposed:exposed-json:0.59.0")
    // drivers
    implementation("org.xerial:sqlite-jdbc:3.49.0.0")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("com.mysql:mysql-connector-j:9.2.0")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11")

    // Web push
    // implementation("dev.blanke.webpush:webpush:6.1.1") // alternative
    implementation("com.interaso:webpush:1.2.0")

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:2.8.0")
    implementation("com.sksamuel.hoplite:hoplite-hocon:2.8.0")
    implementation("com.sksamuel.hoplite:hoplite-yaml:2.8.0")
    implementation("com.sksamuel.hoplite:hoplite-hikaricp:2.8.0")
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.5")
    implementation("org.slf4j:jul-to-slf4j:2.0.16")
    implementation("io.klogging:klogging-jvm:0.9.4")
    implementation("io.klogging:slf4j-klogging:0.9.4")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("io.klogging:klogging-jvm:0.9.4")
}

// Define publication to allow publishing to local maven repo with the command:  ./gradlew publishToMavenLocal
// This should not be published to https://maven.waltid.dev/ to save storage
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("walt.id wallet API REST service")
                description.set(
                    """
                    Kotlin/Java REST service for storing digital credentials
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
}
