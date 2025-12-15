import java.util.Properties

plugins {
    id("waltid.backend") // <-- Applies Kotlin JVM, Toolchain 21, etc.
    id("io.ktor.plugin")
    application
}

// Common Ktor Dependencies (if all services use these)
dependencies {
    val ktorVersion = "3.2.2" // Or load from catalog: libs.findVersion("ktor").get()

    // Core server dependencies
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.5")
}

// Windows Start Script Fix
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        windowsScript.writeText(
            windowsScript.readText().replace(Regex("set CLASSPATH=.*"), "set CLASSPATH=%APP_HOME%\\\\lib\\\\*")
        )
    }
}

// Version Properties Generation
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

// Default Application Settings
application {
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
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
