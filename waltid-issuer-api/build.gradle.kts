import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

object Versions {
    const val KOTLIN_VERSION = "1.9.22" // also change 2 plugins
    const val KTOR_VERSION = "2.3.8" // also change 1 plugin
    const val COROUTINES_VERSION = "1.8.0"
    const val EXPOSED_VERSION = "0.43.0"
    const val HOPLITE_VERSION = "2.8.0.RC3"
}

plugins {
    kotlin("jvm") // Versions.KOTLIN_VERSION
    kotlin("plugin.serialization")  // Versions.KOTLIN_VERSION

    //id("io.ktor.plugin") version "2.3.8" // Versions.KTOR_VERSION
    id("io.ktor.plugin") version "2.3.8" // Versions.KTOR_VERSION
    id("org.owasp.dependencycheck") version "9.1.0"
    id("com.github.jk1.dependency-license-report") version "2.5"
    application
    `maven-publish`

    id("com.github.ben-manes.versions") //version "0.48.0"
}

group = "id.walt"
//version = "1.SNAPSHOT"

repositories {
    mavenCentral()
    //jcenter()
    maven("https://jitpack.io")
    maven("https://maven.waltid.dev/releases")
    mavenLocal()
}


dependencies {

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

    // Ktor server external libs
    implementation("io.github.smiley4:ktor-swagger-ui:2.8.0")

    // Ktor client
    implementation("io.ktor:ktor-client-core-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-serialization-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-json-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-cio-jvm:${Versions.KTOR_VERSION}")
    implementation("io.ktor:ktor-client-logging-jvm:${Versions.KTOR_VERSION}")


    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:${Versions.KTOR_VERSION}")

    // Date
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES_VERSION}")

    /* -- Misc --*/

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.9")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("org.slf4j:jul-to-slf4j:2.0.12")

    // Test
    testImplementation(kotlin("test"))
    //testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Versions.KOTLIN_VERSION}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES_VERSION}")
    //testImplementation("io.ktor:ktor-server-tests-jvm:${Versions.KTOR_VERSION}")

    // OIDC
    api(project(":waltid-openid4vc"))

    // walt.id
    api(project(":waltid-crypto"))
    api(project(":waltid-did"))

    api(project(":waltid-verifiable-credentials"))
    api(project(":waltid-sdjwt"))

    // TODO: REMOVE:
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Use the following condition to optionally run the integration tests:
    // > gradle build -PrunIntegrationTests
    if (!project.hasProperty("runIntegrationTests")) {
        exclude("id/walt/test/integration/**")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
tasks.withType<org.gradle.api.tasks.bundling.Zip> {
    isZip64 = true
}
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        windowsScript.writeText(
            windowsScript.readText().replace(Regex("set CLASSPATH=.*"), "set CLASSPATH=%APP_HOME%\\\\lib\\\\*")
        )
    }
}

application {
    mainClass.set("id.walt.issuer.MainKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            pom {
                name.set("walt.id XYZ Kit")
                description.set(
                    """
                    Kotlin/Java library for XYZ core services
                    """.trimIndent()
                )
                url.set("https://walt.id")
            }
            from(components["java"])
        }
    }

    repositories {
        maven {
            url = uri("https://maven.waltid.dev/releases")
            val envUsername = System.getenv("MAVEN_USERNAME")
            val envPassword = System.getenv("MAVEN_PASSWORD")

            val usernameFile = File("secret_maven_username.txt")
            val passwordFile = File("secret_maven_password.txt")

            val secretMavenUsername = envUsername ?: usernameFile.let { if (it.isFile) it.readLines().first() else "" }
            val secretMavenPassword = envPassword ?: passwordFile.let { if (it.isFile) it.readLines().first() else "" }

            credentials {
                username = secretMavenUsername
                password = secretMavenPassword
            }
        }
    }
}

/*licenseReport {
    renderers = arrayOf<ReportRenderer>(InventoryHtmlReportRenderer("xyzkit-licenses-report.html", "XYZ Kit"))
    filters = arrayOf<DependencyFilter>(LicenseBundleNormalizer())
}*/
