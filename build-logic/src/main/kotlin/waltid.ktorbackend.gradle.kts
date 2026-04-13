plugins {
    id("waltid.backend") // <-- Applies Kotlin JVM, Toolchain 21, etc.
    id("io.ktor.plugin")
    application
    id("com.github.gmazzo.buildconfig")
}

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val identityLibs = catalogs.named("identityLibs")
val ktorVersion = identityLibs.findVersion("ktor").get().requiredVersion
val kotlinLoggingVersion = identityLibs.findVersion("kotlinlogging").get().requiredVersion

// Common Ktor Dependencies (if all services use these)
dependencies {
    // Core server dependencies
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:$kotlinLoggingVersion")
}

// Windows Start Script Fix
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        windowsScript.writeText(
            windowsScript.readText().replace(Regex("set CLASSPATH=.*"), "set CLASSPATH=%APP_HOME%\\\\lib\\\\*")
        )
    }
}

// Default Application Settings
application {
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

buildConfig {
    useKotlinOutput()
    buildConfigField("String", "VERSION", "\"${project.version}\"")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Use the following condition to optionally run the integration tests:
    // > gradle build -PrunIntegrationTests
    if (!project.hasProperty("runIntegrationTests")) {
        exclude("id/walt/test/integration/**")
    }
}

tasks.named<Zip>("shadowJar") {
    isZip64 = true
}
