@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.itb

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.AriaRole
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.regex.Pattern
import kotlin.system.exitProcess

/** Explicit opt-in entry point. Ordinary unit tests never sign in to or start sessions on the remote tenant. */
fun main(): Unit = runBlocking {
    val exit = try { runItb() } catch (error: Exception) {
        // Configuration errors and browser exceptions may carry authentication values.
        System.err.println("ITB runner setup failed (${error::class.simpleName}); no passing result is implied.")
        2
    }
    exitProcess(exit)
}

private suspend fun runItb(): Int {
    fun required(name: String) = requireNotNull(System.getenv(name)?.takeIf(String::isNotBlank)) { "$name is required" }
    val revision = required("ITB_BUILD_REVISION").also { require(it.matches(Regex("[0-9a-f]{40}(\\+dirty)?"))) }
    val catalogue = ItbCatalogue.initialWalletCases()
    val origin = catalogue.origin
    val directory = Path.of(System.getenv("ITB_REPORT_DIR") ?: "build/reports/itb-wallet")
    val selected = ItbRunSelection.select(catalogue, System.getenv("ITB_CASES"))
    val actorVariables = mapOf(
        "Base Protocols" to "ITB_BASE_ACTOR_KEY", "domain specific" to "ITB_DOMAIN_ACTOR_KEY",
        "Payment use cases" to "ITB_PAYMENT_ACTOR_KEY",
    )
    val started = Instant.now().toString()
    val results = selected.map { (suite, case) ->
        ItbCaseResult(suite.id, case.id, null, ItbCaseResult.Outcome.NOT_RUN, ItbCaseResult.Phase.START,
            false, false, null, false, null, started, started)
    }.toMutableList()
    fun writeReport() = ItbReportWriter.write(directory, ItbRunReport(revision, catalogue.observedOn, results.toList()))
    writeReport()

    val organisationKey = required("ITB_ORGANISATION_KEY")
    val systemKey = required("ITB_SYSTEM_KEY")
    val username = required("ITB_USERNAME")
    val password = required("ITB_PASSWORD")
    val organisationId = required("ITB_ORGANISATION_ID").also { require(it.matches(Regex("[0-9]+"))) }
    val actorKeys = selected.map { it.first.specification }.distinct()
        .associateWith { required(actorVariables.getValue(it)) }

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
                page.navigate("${catalogue.testBed}/app#/login")
                page.getByRole(AriaRole.TEXTBOX, Page.GetByRoleOptions().setName("Username:")).fill(username)
                page.getByRole(AriaRole.TEXTBOX, Page.GetByRoleOptions().setName("Password:")).fill(password)
                page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(Pattern.compile("Log in"))).click()
                page.waitForURL("**/#/home")
                itbHttpClient().use { client ->
                    val wallet = ItbWalletDriver.create(client, origin, trust) { url, callback ->
                        ItbReferenceAuthorization.resolve(client, origin, url, callback)
                    }
                    client.config { followRedirects = false }.use { restClient ->
                        val api = ItbRestClient(restClient, Url("${catalogue.testBed}/api/rest"), organisationKey)
                        val bridge = ItbPortalBridge(page, "${catalogue.testBed}/app#/organisation/tests/$organisationId")
                        val runner = ItbCaseRunner(api, bridge, wallet::execute)
                        selected.forEachIndexed { index, (suite, case) ->
                            results[index] = runner.run(systemKey, actorKeys.getValue(suite.specification), suite.id, case.id)
                            writeReport()
                            println("${suite.id}/${case.id}: ${results[index].outcome}")
                        }
                    }
                }
            }
        }
    }
    return if (results.all { it.outcome == ItbCaseResult.Outcome.PASSED }) 0 else 1
}

internal object ItbRunSelection {
    private val prerequisites = mapOf(
        "tc_vp_001" to "tc_vci_006", "tc_vp_002" to "tc_vci_006", "tc_vp_003" to "tc_vci_006",
        "tc_vp_007" to "tc_vci_008", "tc15" to "tc_vci_006",
        "ts12_pay_01" to "ts12_issue_01", "ts12_pay_02" to "ts12_issue_02", "ts12_pay_03" to "ts12_issue_03",
        "ts12_pay_dc_api_01" to "ts12_issue_01", "ts12_pay_dc_api_02" to "ts12_issue_02", "ts12_pay_dc_api_03" to "ts12_issue_03",
    )

    fun select(catalogue: ItbCatalogue, selection: String?): List<Pair<ItbCatalogue.Suite, ItbCatalogue.Case>> {
        val all = catalogue.suites.flatMap { suite -> suite.cases.map { suite to it } }
        if (selection == null) return all
        val ids = selection.split(',').map(String::trim).toSet()
        require(ids.isNotEmpty() && ids.all { id -> all.any { it.second.id == id } }) { "Unknown or empty ITB case selection" }
        val expanded = ids + ids.mapNotNull(prerequisites::get)
        return all.filter { it.second.id in expanded }
    }
}
