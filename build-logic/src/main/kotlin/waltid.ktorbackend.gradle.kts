plugins {
    id("waltid.backend") // <-- Applies Kotlin JVM, Toolchain 21, etc.
    id("io.ktor.plugin")
    application
}

// Common Ktor Dependencies (if all services use these)
dependencies {
    val ktorVersion = "3.3.3" // Or load from catalog: identityLibs.findVersion("ktor").get()

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
/*val generateVersionProperties by tasks.registering(WriteProperties::class) {
    destinationFile.set(layout.buildDirectory.file("generated/resources/version.properties"))

    property("version", rootProject.version.toString())
    comment = "walt.id version store"
}
sourceSets.named("main") {
    resources.srcDir(generateVersionProperties)
}*/

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
