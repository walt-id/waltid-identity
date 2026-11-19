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
    val selected = ItbRunSelection.select(catalogue, System.getenv("ITB_CASES"), unattendedOnly = System.getenv("CI") == "true")
    val started = Instant.now().toString()
    val androidPort = System.getenv("ITB_ANDROID_PORT")?.toInt()?.also { require(it in 1024..65535) }
    val androidApkSha256 = androidPort?.let {
        required("ITB_ANDROID_APK_SHA256").also { require(it.matches(Regex("[0-9a-f]{64}"))) }
    }
    val results = selected.map { (suite, case) ->
        ItbCaseResult(suite.id, case.id, null, ItbCaseResult.Outcome.NOT_RUN, ItbCaseResult.Phase.START,
            false, false, null, false, null, started, started)
    }.toMutableList()
    fun writeReport() = ItbReportWriter.write(directory, ItbRunReport(
        revision, catalogue.observedOn, results.toList(),
        walletExecution = if (androidPort == null) ItbRunReport.WalletExecution.JVM_SOFTWARE else ItbRunReport.WalletExecution.ANDROID_NATIVE,
        keyAttestation = ItbRunReport.KeyAttestation.SYNTHETIC_TEST_FIXTURE,
        androidApkSha256 = androidApkSha256,
    ))
    writeReport()

    val organisationKey = required("ITB_ORGANISATION_KEY")
    val username = required("ITB_USERNAME")
    val password = required("ITB_PASSWORD")
    val organisationId = required("ITB_ORGANISATION_ID").also { require(it.matches(Regex("[0-9]+"))) }

    // This test-only CA is pinned from the official EUDI reference wallet, independently of request x5c.
    val pem = System.getenv("ITB_X509_TRUST_ANCHORS")?.let { Files.readString(Path.of(it)) }
        ?: requireNotNull(ItbCatalogue::class.java.getResource("/itb/pidissuerca02-eu.pem")).readText()
    val certificates = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----")
        .findAll(pem).map { X509CertificateUtil.parseCertificatePem(it.value) }.toList()
    require(certificates.isNotEmpty()) { "The ITB trust anchor file has no certificates" }
    val trust = ClientIdTrustConfiguration(x509TrustAnchors = InMemoryTrustStore(certificates))

    Playwright.create().use { playwright ->
        playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true)).use { browser ->
            browser.newContext(Browser.NewContextOptions().setViewportSize(1440, 1000)).use { context ->
                val page = context.newPage()
                page.setDefaultTimeout(15_000.0)
                ItbPortalLogin.login(page, catalogue.testBed, username, password)
                itbHttpClient().use { client ->
                    val native = androidPort?.let {
                        ItbAndroidWalletDriver.connect(it, required("ITB_ANDROID_TOKEN"), origin, pem, Dispatchers.IO)
                    }
                    try {
                        val executeWallet: suspend (ItbWalletInteraction) -> Unit = native?.let { it::execute }
                            ?: ItbWalletDriver.create(client, origin, trust) { url, callback ->
                                ItbReferenceAuthorization.resolve(client, origin, url, callback)
                            }.let { it::execute }
                        client.config { followRedirects = false }.use { restClient ->
                            val api = ItbRestClient(restClient, Url("${catalogue.testBed}/api/rest"), organisationKey)
                            val bridge = ItbPortalBridge(page, "${catalogue.testBed}/app#/organisation/conformance/$organisationId", catalogue.systemName)
                            val runner = ItbCaseRunner(api, bridge, executeWallet)
                            selected.forEachIndexed { index, (suite, case) ->
                                results[index] = runner.run(suite, case)
                                writeReport()
                                println("${suite.id}/${case.id}: ${results[index].outcome}")
                            }
                        }
                    } finally { native?.close() }
                }
            }
        }
    }
    return if (results.all { it.outcome == ItbCaseResult.Outcome.PASSED }) 0 else 1
}

internal object ItbRunSelection {
    private val unattendedCases = setOf(
        "cs01v1/tc_vci_001", "cs01v1/tc_vci_002", "cs01v1/tc_vci_003", "cs01v1/tc_vci_005",
        "cs01v1/tc_vci_006", "cs01v1/tc_vci_007", "cs01v1/tc_vci_008",
        "cs02v1/tc_vp_001", "cs02v1/tc_vp_002", "cs02v1/tc_vp_003", "cs02v1/tc_vp_007",
        "cs02v1/tc_vp_011", "cs02v1/tc_vp_012", "cs02v1/tc_vp_013", "cts07/tc15",
        "cs10-test-suite/rv_iss_01", "cs10-test-suite/rv_iss_02",
        "ts12/ts12_issue_01", "ts12/ts12_issue_02", "ts12/ts12_issue_03",
    )

    private val prerequisites = mapOf(
        "cs02v1/tc_vp_001" to "cs01v1/tc_vci_006",
        "cs02v1/tc_vp_002" to "cs01v1/tc_vci_006",
        "cs02v1/tc_vp_003" to "cs01v1/tc_vci_006",
        "cs02v1/tc_vp_007" to "cs01v1/tc_vci_008",
        "cs02v1/tc_vp_011" to "cs01v1/tc_vci_006",
        "cs02v1/tc_vp_012" to "cs01v1/tc_vci_006",
        "cs02v1/tc_vp_013" to "cs01v1/tc_vci_006",
        "cts07/tc15" to "cs01v1/tc_vci_006",
        "ts12/ts12_pay_01" to "ts12/ts12_issue_01",
        "ts12/ts12_pay_02" to "ts12/ts12_issue_02",
        "ts12/ts12_pay_03" to "ts12/ts12_issue_03",
        "ts12/ts12_pay_dc_api_01" to "ts12/ts12_issue_01",
        "ts12/ts12_pay_dc_api_02" to "ts12/ts12_issue_02",
        "ts12/ts12_pay_dc_api_03" to "ts12/ts12_issue_03",
    )

    fun select(catalogue: ItbCatalogue, selection: String?, unattendedOnly: Boolean = false): List<Pair<ItbCatalogue.Suite, ItbCatalogue.Case>> {
        val all = catalogue.suites.flatMap { suite -> suite.cases.map { suite to it } }
            .associateBy { (suite, case) -> "${suite.id}/${case.id}" }
        val requested = if (selection == null) {
            if (unattendedOnly) unattendedCases else all.keys
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
        val expanded = requested + requested.mapNotNull(prerequisites::get)
        require(all.keys.containsAll(expanded)) { "A selected ITB case or prerequisite is missing from the catalogue" }
        if (unattendedOnly) require(expanded.all { it in unattendedCases }) {
            "The selected ITB case is outside the unattended inventory"
        }
        return all.filterKeys { it in expanded }.values.toList()
    }
}
