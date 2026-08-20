@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import java.security.KeyStore
import java.security.cert.CertificateFactory

object Versions {
    const val HOPLITE_VERSION = "2.9.0"
    const val PLAYWRIGHT_VERSION = "1.60.0"
}

plugins {
    id("waltid.ktorbackend")
    id("waltid.ktordocker")
}

group = "id.walt"

dependencies {
    api(project(":waltid-services:waltid-service-commons"))

    /* -- KTOR -- */

    // Ktor server
    implementation(identityLibs.ktor.server.core)
    implementation(identityLibs.ktor.server.auth)
    implementation(identityLibs.ktor.server.sessions)
    implementation(identityLibs.ktor.server.authjwt)
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

    // Ktor client
    implementation(identityLibs.ktor.client.core)
    implementation(identityLibs.ktor.client.serialization)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.client.json)
    implementation(identityLibs.ktor.client.java)
    implementation(identityLibs.ktor.client.cio)
    implementation(identityLibs.ktor.client.logging)


    /* -- Kotlin -- */

    // Kotlinx.serialization
    implementation(identityLibs.ktor.serialization.kotlinx.json)

    // Date
    implementation(identityLibs.kotlinx.datetime)

    // Coroutines
    implementation(identityLibs.kotlinx.coroutines.core)

    /* -- Misc --*/

    // Config
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)
    implementation(identityLibs.klogging)
    implementation(identityLibs.slf4j.klogging)
    implementation("io.ktor:ktor-client-encoding:3.2.2")
    implementation("com.microsoft.playwright:playwright:${Versions.PLAYWRIGHT_VERSION}") {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
        exclude(group = "org.opentest4j")
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }

    // Test

    implementation(identityLibs.nimbus.jose.jwt)

    implementation(identityLibs.kotlintest)
    testImplementation(identityLibs.kotlinx.coroutines.test)
    implementation(project(":waltid-libraries:protocols:waltid-openid4vp-wallet"))
    implementation(project(":waltid-libraries:credentials:waltid-holder-policies"))

    implementation(project(":waltid-services:waltid-service-commons-test"))
    implementation(project(":waltid-services:waltid-verifier-api2"))
    // Wallet2 is started in-process for the wallet conformance runs, like Verifier2 is
    implementation(project(":waltid-services:waltid-wallet-api2"))
    // Used directly to mint the credential provisioned into the wallet under test
    api(project(":waltid-libraries:sdjwt:waltid-sdjwt"))
    // Used directly to issue the mdoc provisioned into the wallet under test
    api(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))

    api(project(":waltid-libraries:credentials:waltid-dcql"))
    api(project(":waltid-libraries:credentials:waltid-digital-credentials"))
    api(project(":waltid-libraries:credentials:waltid-verification-policies2"))
    api(project(":waltid-libraries:protocols:waltid-openid4vp"))
    api(project(":waltid-libraries:protocols:waltid-openid4vp-verifier"))
    // Used directly to derive x509_hash client identifiers, so declared rather than relied on transitively
    api(project(":waltid-libraries:protocols:waltid-openid4vp-clientidprefix"))
    // Used directly to mint the test attester's certificate chain for HAIP client attestation
    api(project(":waltid-libraries:crypto:waltid-x509"))
    // Used directly for client attestation JWT claim and type constants
    api(project(":waltid-libraries:protocols:waltid-openid4vci"))
    api(project(":waltid-libraries:protocols:waltid-openid4vp-verifier-openapi"))
    implementation(project(":waltid-libraries:web:waltid-ktor-notifications"))
}

application {
    mainClass.set("id.walt.openid4vp.conformance.MainKt")
}

ktor {
    docker {
        portMappings.set(
            listOf(
                DockerPortMapping(7003, 7003, DockerPortMappingProtocol.TCP)
            )
        )
    }
}

val conformanceTruststorePath = providers.environmentVariable("CONFORMANCE_TRUSTSTORE_PATH")
    .orElse(file("conformance-truststore.jks").absolutePath)
val conformanceTruststorePassword = providers.environmentVariable("CONFORMANCE_TRUSTSTORE_PASSWORD")
    .orElse("changeit")

val skipLiveConformance = (
    (findProperty("skipLiveConformance") as String?) ?: System.getenv("SKIP_LIVE_CONFORMANCE")
).equals("true", ignoreCase = true)

// The committed truststore matches the docker-compose flow, where
// run-issuer-conformance-local.sh generates its own nginx certificate and imports it.
// A devenv-based suite instead terminates TLS with an mkcert certificate, whose root CA lives at
// <conformance-suite>/.devenv/state/mkcert/rootCA.pem. Point CONFORMANCE_EXTRA_CA_PEM at that file
// (or any other PEM) to have it trusted in addition to the committed anchors.
val conformanceExtraCaPem = providers.environmentVariable("CONFORMANCE_EXTRA_CA_PEM")
    // A devenv-run suite keeps its mkcert root CA inside its own checkout, so look there when the
    // variable is not set. Saves passing CONFORMANCE_EXTRA_CA_PEM on every single run.
    .orElse(
        providers.provider {
            sequenceOf("conformance-suite", "../conformance-suite")
                .map { rootDir.resolve("$it/.devenv/state/mkcert/rootCA.pem") }
                .firstOrNull { it.isFile }
                ?.absolutePath
        }
    )
val derivedConformanceTruststore = layout.buildDirectory.file("conformance/conformance-truststore.jks")

val prepareConformanceTruststore = tasks.register("prepareConformanceTruststore") {
    group = "verification"
    description = "Derive a test truststore that additionally trusts CONFORMANCE_EXTRA_CA_PEM."

    // Captured as locals so the task actions stay configuration-cache compatible
    // (referencing build-script members such as file(..) or logger would capture the script).
    val basePath = conformanceTruststorePath
    val extraCaPath = conformanceExtraCaPem
    val password = conformanceTruststorePassword
    val output = derivedConformanceTruststore

    onlyIf { extraCaPath.isPresent }

    inputs.files(basePath, extraCaPath).withPropertyName("trustSources")
    inputs.property("truststorePassword", password)
    outputs.file(output)

    doLast {
        val extraCa = File(extraCaPath.get())
        require(extraCa.isFile) { "CONFORMANCE_EXTRA_CA_PEM does not point at a file: $extraCa" }

        val secret = password.get().toCharArray()
        val store = KeyStore.getInstance("JKS").apply {
            File(basePath.get()).inputStream().use { load(it, secret) }
        }
        // A PEM may carry a whole chain, so import every certificate it contains.
        extraCa.inputStream().buffered().use { pem ->
            CertificateFactory.getInstance("X.509").generateCertificates(pem)
        }.forEachIndexed { index, certificate ->
            store.setCertificateEntry("conformance-extra-ca-$index", certificate)
        }

        val target = output.get().asFile
        target.parentFile.mkdirs()
        target.outputStream().use { store.store(it, secret) }
        println("Conformance truststore (base + $extraCa) written to $target")
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(prepareConformanceTruststore)
    systemProperty(
        "javax.net.ssl.trustStore",
        conformanceExtraCaPem
            .map { derivedConformanceTruststore.get().asFile.absolutePath }
            .orElse(conformanceTruststorePath)
            .get()
    )
    systemProperty("javax.net.ssl.trustStorePassword", conformanceTruststorePassword.get())
    // Gradle's test JVM is a separate process, so the selector has to be forwarded explicitly.
    // See VpWalletConformanceTests.selectedVariants.
    ((findProperty("conformance.wallet.variants") as String?)
        ?: System.getProperty("conformance.wallet.variants"))
        ?.let { systemProperty("conformance.wallet.variants", it) }
    if (skipLiveConformance) {
        filter {
            isFailOnNoMatchingTests = false
            excludeTestsMatching("id.walt.openid4vp.conformance.ConformanceTests*")
            excludeTestsMatching("id.walt.openid4vp.conformance.IssuerConformanceTests*")
            excludeTestsMatching("id.walt.openid4vp.conformance.VerifierConformanceTests*")
            excludeTestsMatching("id.walt.openid4vp.conformance.VciWalletConformanceTests*")
            excludeTestsMatching("id.walt.openid4vp.conformance.VpWalletConformanceTests*")
            excludeTestsMatching("id.walt.openid4vp.conformance.WalletPresentConformanceTests*")
            excludeTestsMatching("id.walt.openid4vp.conformance.IsolatedWalletConformanceTest*")
        }
    }
}

fun selectedPlaywrightBrowser(): String = when (
    ((findProperty("playwright.browser") as String?) ?: System.getProperty("playwright.browser")
    ?: System.getenv("PLAYWRIGHT_BROWSER"))
        ?.trim()
        ?.lowercase()
) {
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
    null, "", "true", "1", "yes", "on" -> true
    "false", "0", "no", "off" -> false
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

tasks.register<JavaExec>("installPlaywrightBrowsers") {
    group = "verification"
    description = "Install the Playwright browser used by issuer conformance authorization-code tests."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.microsoft.playwright.CLI")
    args(playwrightInstallArgs())
}

fun registerWalletProfileTestTask(taskName: String, testFilter: String, descriptionText: String) {
    tasks.register<Test>(taskName) {
        group = "verification"
        description = descriptionText

        testClassesDirs = tasks.test.get().testClassesDirs
        classpath = tasks.test.get().classpath

        useJUnitPlatform()
        filter {
            includeTestsMatching(testFilter)
        }
    }
}

registerWalletProfileTestTask(
    taskName = "vciWalletSdJwtVcDpopAuthorizationCode",
    testFilter = "id.walt.openid4vp.conformance.VciWalletConformanceTests.vciWalletSdJwtVcDpopAuthorizationCode",
    descriptionText = "Run the SD-JWT VC + DPoP + authorization_code VCI wallet conformance profile."
)

registerWalletProfileTestTask(
    taskName = "vciWalletIsoMdocDpopAuthorizationCode",
    testFilter = "id.walt.openid4vp.conformance.VciWalletConformanceTests.vciWalletIsoMdocDpopAuthorizationCode",
    descriptionText = "Run the ISO mdoc + DPoP + authorization_code VCI wallet conformance profile."
)

registerWalletProfileTestTask(
    taskName = "vciWalletSdJwtVcAuthorizationCodeHaipFullTarget",
    testFilter = "id.walt.openid4vp.conformance.VciWalletConformanceTests.vciWalletSdJwtVcAuthorizationCodeHaipFullTarget",
    descriptionText = "Run the HAIP full-target VCI wallet conformance profile."
)
