@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.itb

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import io.ktor.http.Url
import io.klogging.config.loggingConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.system.exitProcess

/** Explicit opt-in entry point. Ordinary unit tests never sign in to or start sessions on the remote tenant. */
fun main(): Unit = runBlocking {
    // Protocol libraries can log headers and bearer URLs. This CLI emits only sanitized outcomes.
    loggingConfiguration {}
    val exit = try { runItb() } catch (error: Exception) {
        // Configuration errors and browser exceptions may carry authentication values.
        val code = (error as? ItbPortalStepTimeout)?.step?.let { ":$it" }.orEmpty()
        System.err.println("ITB runner setup failed (${error::class.simpleName}$code); no passing result is implied.")
        2
    }
    exitProcess(exit)
}

private suspend fun runItb(): Int {
    fun required(name: String) = requireNotNull(System.getenv(name)?.takeIf(String::isNotBlank)) { "$name is required" }
    val revision = required("ITB_BUILD_REVISION").also { require(it.matches(Regex("[0-9a-f]{40}(\\+dirty)?"))) }
    val catalogue = ItbCatalogue.walletCases()
    val origin = catalogue.origin
    val directory = Path.of(System.getenv("ITB_REPORT_DIR") ?: "build/reports/itb-wallet")
    val demoMode = System.getenv("ITB_ANDROID_DEMO_MODE")?.also { require(it in setOf("attended", "unattended")) }
    val ci = System.getenv("CI") == "true"
    require(!ci || demoMode != "attended") { "Attended demo tests require an operator outside CI" }
    val selected = ItbRunSelection.select(
        catalogue, System.getenv("ITB_CASES"),
        unattendedOnly = ci || demoMode == "unattended", demoApp = demoMode != null,
    )
    val started = Instant.now().toString()
    val androidPort = System.getenv("ITB_ANDROID_PORT")?.toInt()?.also { require(it in 1024..65535) }
    val androidApkSha256 = androidPort?.let {
        required("ITB_ANDROID_APK_SHA256").also { require(it.matches(Regex("[0-9a-f]{64}"))) }
    }
    val appApkSha256 = if (demoMode == null) null else {
        checkNotNull(androidPort) { "The demo app needs the device bridge" }
        required("ITB_WALLET_APP_APK_SHA256").also { require(it.matches(Regex("[0-9a-f]{64}"))) }
    }
    val execution = when (demoMode) {
        "unattended" -> ItbRunReport.WalletExecution.ANDROID_DEMO_UNATTENDED
        "attended" -> ItbRunReport.WalletExecution.ANDROID_DEMO_ATTENDED
        else -> if (androidPort == null) ItbRunReport.WalletExecution.JVM_SOFTWARE else ItbRunReport.WalletExecution.ANDROID_NATIVE
    }
    val results = selected.map { (suite, case) ->
        ItbCaseResult(suite.id, case.id, null, ItbCaseResult.Outcome.NOT_RUN, ItbCaseResult.Phase.START,
            false, false, null, false, null, started, started)
    }.toMutableList()
    fun writeReport() = ItbReportWriter.write(directory, ItbRunReport(
        revision, catalogue.observedOn, results.toList(),
        walletExecution = execution,
        keyAttestation = ItbRunReport.KeyAttestation.SYNTHETIC_TEST_FIXTURE,
        androidApkSha256 = androidApkSha256,
        walletAppApkSha256 = appApkSha256,
    ))
    writeReport()

    val organisationKey = required("ITB_ORGANISATION_KEY")
    val username = required("ITB_USERNAME")
    val password = required("ITB_PASSWORD")
    val organisationId = required("ITB_ORGANISATION_ID").also { require(it.matches(Regex("[0-9]+"))) }

    // SDK diagnostics pin the EUDI reference CA independently of request x5c.
    val sdkTrustPem = if (demoMode == null) {
        System.getenv("ITB_X509_TRUST_ANCHORS")?.let { Files.readString(Path.of(it)) }
            ?: requireNotNull(ItbCatalogue::class.java.getResource("/itb/pidissuerca02-eu.pem")).readText()
    } else null
    val jvmTrust = if (androidPort == null) {
        val certificates = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----")
            .findAll(checkNotNull(sdkTrustPem)).map { X509CertificateUtil.parseCertificatePem(it.value) }.toList()
        require(certificates.isNotEmpty()) { "The ITB trust anchor file has no certificates" }
        ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(certificates))
    } else null

    val showPortal = !ci && System.getenv("ITB_SHOW_BROWSER") == "true"
    Playwright.create().use { playwright ->
        playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(!showPortal).setArgs(listOf("--disable-http2"))).use { browser ->
            val options = Browser.NewContextOptions()
            if (showPortal) options.setViewportSize(null) else options.setViewportSize(1440, 1000)
            browser.newContext(options).use { context ->
                val page = context.newPage()
                page.setDefaultTimeout(15_000.0)
                ItbPortalLogin.login(page, catalogue.testBed, username, password)
                itbHttpClient().use { client ->
                    val caseTimeoutMillis = if (demoMode == null) 120_000L else if (demoMode == "attended") 300_000L else 210_000L
                    val native = androidPort?.let {
                        ItbAndroidWalletDriver.connect(
                            it, required("ITB_ANDROID_TOKEN"), origin, sdkTrustPem, Dispatchers.IO,
                            provider = demoMode?.let { "android-demo-$it" } ?: "android-native-biometric",
                            operationTimeoutMillis = caseTimeoutMillis - 10_000,
                        )
                    }
                    val chromePort = if (demoMode != null && selected.any { (suite, case) ->
                        PortalInput.forCase(suite.id, case.id).usesDcApi
                    }) required("ITB_CHROME_PORT").toInt().also { require(it in 1024..65535) } else null
                    try {
                        val driver = native?.let { it::execute } ?: ItbWalletDriver.create(
                            client, origin, checkNotNull(jvmTrust),
                        ) { url, callback ->
                            ItbReferenceAuthorization.resolve(client, origin, url, callback)
                        }.let { it::execute }
                        client.config { followRedirects = false }.use { restClient ->
                            val chrome = chromePort?.let {
                                playwright.chromium().connectOverCDP("http://127.0.0.1:$it",
                                    BrowserType.ConnectOverCDPOptions().setTimeout(20_000.0))
                            }
                            try {
                                val androidBrowser = chrome?.let { ItbAndroidBrowser(it, restClient, origin, required("ANDROID_SERIAL")) }
                                val executeWallet: suspend (ItbWalletInteraction) -> ItbWalletResult = { interaction ->
                                    when {
                                        androidBrowser != null && interaction is ItbWalletInteraction.DigitalCredentials ->
                                            androidBrowser.present(interaction, driver)
                                        androidBrowser != null && interaction is ItbWalletInteraction.DigitalIssuance ->
                                            androidBrowser.issue(interaction, driver)
                                        else -> driver(interaction)
                                    }
                                }
                                val api = ItbRestClient(restClient, Url("${catalogue.testBed}/api/rest"), organisationKey)
                                val bridge = ItbPortalBridge(page, "${catalogue.testBed}/app#/organisation/conformance/$organisationId", catalogue.systemName)
                                val runner = ItbCaseRunner(
                                    api, bridge, executeWallet, caseTimeoutMillis = caseTimeoutMillis,
                                    walletConnected = { native?.isClosed != true },
                                )
                                val completed = mutableMapOf<String, ItbCaseResult>()
                                selected.forEachIndexed { index, (suite, case) ->
                                    val prerequisite = case.prerequisite?.let { ItbRunSelection.prerequisiteCredential(completed[it]) }
                                    val result = runner.run(suite, case, prerequisite)
                                    results[index] = result
                                    completed["${suite.id}/${case.id}"] = result
                                    writeReport()
                                    println("${suite.id}/${case.id}: ${results[index].outcome}")
                                }
                            } finally { chrome?.close() }
                        }
                    } finally { native?.close() }
                }
            }
        }
    }
    return if (results.all { it.outcome == ItbCaseResult.Outcome.PASSED }) 0 else 1
}

internal object ItbRunSelection {
    fun select(
        catalogue: ItbCatalogue,
        selection: String?,
        unattendedOnly: Boolean = false,
        demoApp: Boolean = false,
    ): List<Pair<ItbCatalogue.Suite, ItbCatalogue.Case>> {
        val all = catalogue.suites.flatMap { suite -> suite.cases.map { suite to it } }
            .associateBy { (suite, case) -> "${suite.id}/${case.id}" }
        fun available(case: ItbCatalogue.Case) = (!case.attended || !unattendedOnly) && (!case.demoOnly || demoApp)
        val requested = if (selection == null) {
            all.filterValues { (suite, case) -> !suite.optIn && available(case) }.keys
        } else {
            selection.split(',').map { selector ->
                val id = selector.trim()
                if ('/' in id) {
                    require(id in all) { "Unknown qualified ITB case selection" }
                    id
                } else {
                    requireNotNull(all.entries.singleOrNull { it.value.second.id == id }) {
                        "Unknown or ambiguous ITB case selection. Use suite/case for a repeated case ID"
                    }.key
                }
            }.toSet()
        }
        val selected = linkedMapOf<String, Pair<ItbCatalogue.Suite, ItbCatalogue.Case>>()
        val visiting = mutableSetOf<String>()
        fun include(id: String) {
            if (id in selected) return
            val entry = requireNotNull(all[id]) { "An ITB prerequisite is missing from the catalogue" }
            require(available(entry.second)) { "The selected ITB case is unavailable with this driver" }
            require(visiting.add(id)) { "Cyclic ITB prerequisites" }
            entry.second.prerequisite?.let(::include)
            visiting.remove(id)
            selected[id] = entry
        }
        // Expand prerequisites while preserving catalogue order for independent cases.
        val expanded = requested.toMutableSet()
        var size: Int
        do {
            size = expanded.size
            expanded.addAll(expanded.mapNotNull { all[it]?.second?.prerequisite })
        } while (expanded.size != size)
        all.keys.filter(expanded::contains).forEach(::include)
        return selected.values.toList()
    }

    fun prerequisiteCredential(result: ItbCaseResult?): String? =
        result?.takeIf { it.outcome == ItbCaseResult.Outcome.PASSED }?.issuedCredentialIds?.singleOrNull()
}
