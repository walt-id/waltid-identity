import io.ktor.plugin.features.*

object Versions {
    const val HOPLITE_VERSION = "2.9.0"
}

plugins {
    id("waltid.ktorbackend")
    id("waltid.ktordocker")
}

group = "id.walt"

dependencies {
    api(project(":waltid-services:waltid-service-commons"))

    implementation(identityLibs.ktor.server.core)
    implementation(identityLibs.ktor.server.auth)
    implementation(identityLibs.ktor.server.auto.head.response)
    implementation(identityLibs.ktor.server.double.receive)
    implementation(identityLibs.ktor.server.host.common)
    implementation(identityLibs.ktor.server.status.pages)
    implementation(identityLibs.ktor.server.compression)
    implementation(identityLibs.ktor.server.cors)
    implementation(identityLibs.ktor.server.forwarded.header)
    implementation(identityLibs.ktor.server.call.logging)
    implementation(identityLibs.ktor.server.call.id)
    implementation(identityLibs.ktor.server.content.negotiation)
    implementation(identityLibs.ktor.server.cio)
    implementation(identityLibs.ktor.server.sse)

    implementation(identityLibs.ktor.client.core)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.client.java)
    implementation(identityLibs.ktor.client.logging)

    implementation(identityLibs.ktor.serialization.kotlinx.json)
    implementation(identityLibs.kotlinx.coroutines.core)
    implementation(identityLibs.kotlinx.datetime)
    implementation(identityLibs.jsonpathkt)
    implementation(identityLibs.jaywayjsonpath)

    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)

    api(project(":waltid-libraries:protocols:waltid-openid4vci"))
    api(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto2"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto2-migration-v1"))
    implementation(project(":waltid-libraries:crypto:waltid-jose"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-aws"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto-azure"))
    implementation(project(":waltid-libraries:crypto:waltid-x509"))
    implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials"))
    implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
    implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
    implementation(project(":waltid-libraries:web:waltid-ktor-notifications"))
    api(project(":waltid-libraries:waltid-did"))

    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
    testImplementation(identityLibs.bundles.waltid.ktortesting)
    testImplementation(project(":waltid-libraries:protocols:waltid-openid4vci-wallet"))
    testImplementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
    testImplementation(identityLibs.junit.jupiter.api)
    testImplementation("com.microsoft.playwright:playwright:1.60.0") {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
        exclude(group = "org.opentest4j")
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
    testRuntimeOnly(identityLibs.junit.jupiter.engine)
    testRuntimeOnly(identityLibs.junit.platform.launcher)
}

application {
    mainClass.set("id.walt.issuer2.MainKt")
}

buildConfig {
    packageName("id.walt.issuer2")
}

ktor {
    docker {
        portMappings.set(listOf(DockerPortMapping(7002, 7002, DockerPortMappingProtocol.TCP)))
    }
}

fun selectedPlaywrightBrowser(): String = when (System.getenv("PLAYWRIGHT_BROWSER")?.trim()?.lowercase()) {
    null, "" -> "chromium"
    "chromium", "chrome" -> "chromium"
    "firefox" -> "firefox"
    "webkit", "safari" -> "webkit"
    else -> error("Unsupported PLAYWRIGHT_BROWSER value. Expected one of: chromium, firefox, webkit")
}

fun playwrightInstallWithDeps(): Boolean = when (
    ((findProperty("playwright.installWithDeps") as String?) ?: System.getenv("PLAYWRIGHT_INSTALL_WITH_DEPS"))
        ?.trim()
        ?.lowercase()
) {
    null, "", "false", "0", "no", "off" -> false
    "true", "1", "yes", "on" -> true
    else -> error(
        "Unsupported PLAYWRIGHT_INSTALL_WITH_DEPS/playwright.installWithDeps value. Expected true or false"
    )
}

fun playwrightInstallArgs(): List<String> = buildList {
    add("install")
    if (playwrightInstallWithDeps()) {
        add("--with-deps")
    }
    add(selectedPlaywrightBrowser())
}

val installPlaywrightBrowsers = tasks.register<JavaExec>("installPlaywrightBrowsers") {
    group = "verification"
    description = "Install the Playwright browser used by issuer2 Keycloak browser tests."
    classpath = configurations.testRuntimeClasspath.get()
    mainClass.set("com.microsoft.playwright.CLI")
    args(playwrightInstallArgs())
}

tasks.test {
    dependsOn(installPlaywrightBrowsers)
    useJUnitPlatform {
        excludeTags("redis")
    }
}

tasks.register<JavaExec>("verifyPlaywrightBrowser") {
    group = "verification"
    description = "Install and launch the Playwright browser used by issuer2 Keycloak browser tests."
    dependsOn("testClasses", installPlaywrightBrowsers)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("id.walt.issuer2.testsupport.browser.PlaywrightBrowserCheck")
}

tasks.register<Test>("browserTest") {
    description = "Runs issuer2 browser-backed integration tests. Requires the default Keycloak demo configuration."
    group = "verification"

    dependsOn("testClasses", installPlaywrightBrowsers)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("browser")
    }
    shouldRunAfter(tasks.test)
}

tasks.register<Test>("redisTest") {
    description = "Runs issuer2 Redis-backed repository tests. Requires ISSUER2_REDIS_HOST."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("redis")
    }
    shouldRunAfter(tasks.test)
}
