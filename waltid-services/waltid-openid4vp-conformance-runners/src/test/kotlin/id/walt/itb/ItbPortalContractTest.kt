package id.walt.itb

import kotlin.test.*
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

class ItbPortalContractTest {
    private val script = ItbDcScriptFixtures.presentation

    @Test
    fun dcIssuanceUsesTheDeployedPidProfileAndPinsTheIssuerStatusRoute() {
        val script = ItbDcScriptFixtures.issuance
        val issue = ItbPortalBridge.parseDigitalIssuance(script)
        assertEquals("pid-authorization-code", issue.scenario)
        assertEquals("openid4vci-v1", issue.protocol)
        assertFailsWith<IllegalArgumentException> {
            ItbPortalBridge.parseDigitalIssuance(script.replace("openid4vci-v1", "other"))
        }
        assertEquals(
            "https://dev-i4mlab.aegean.gr/rfc-issuer/vci/dc-api/session/owned",
            ItbAndroidBrowser.issuanceStatusEndpoint(issue.endpoint, "owned", "/vci/dc-api/session/owned").toString(),
        )
        for (advertised in listOf("/vci/dc-api/session/other", "https://other.example/vci/dc-api/session/owned",
            "/vci/dc-api/session/owned?token=unexpected")) {
            assertFailsWith<IllegalArgumentException> {
                ItbAndroidBrowser.issuanceStatusEndpoint(issue.endpoint, "owned", advertised)
            }
        }
    }

    @Test
    fun readsTheDeployedScriptsSessionAndProtocolWithoutExecutingIt() {
        val request = ItbPortalBridge.parseDigitalCredentials(script, isPayment = false)
        assertEquals("00000000-0000-0000-0000-000000000001", request.validationSession)
        assertEquals("pid-basic", request.profile)
        assertEquals("openid4vp-v1-signed", request.protocol)
        assertEquals("/rfc-issuer/vp/dc-api/request", request.endpoint.encodedPath)
    }

    @Test
    fun refusesAmbiguousOrChangedScriptInputs() {
        assertFailsWith<IllegalArgumentException> {
            ItbPortalBridge.parseDigitalCredentials(script + "\nconst validationSessionId = 'other-session';", isPayment = false)
        }
        assertFailsWith<IllegalArgumentException> {
            ItbPortalBridge.parseDigitalCredentials(script.replace("const profile = 'pid-basic';", "const profile = resolveProfile();"), isPayment = false)
        }
    }

    @Test
    fun preservesThePaymentFieldsFromTheTs12Script() {
        assertFailsWith<IllegalArgumentException> {
            ItbPortalBridge.parseDigitalCredentials(script, isPayment = true)
        }
        val paymentScript = ItbDcScriptFixtures.payment
        val request = ItbPortalBridge.parseDigitalCredentials(paymentScript, isPayment = true)
        assertEquals("payment-session", request.validationSession)
        assertEquals("ts12-iban", request.profile)
        assertEquals(
            ItbWalletInteraction.Payment("sca-iban", "Coffee Shop", "shop-42", "EUR", "12.34", "tx-dc-api-iban-001"),
            request.payment,
        )
        assertFailsWith<IllegalArgumentException> {
            ItbPortalBridge.parseDigitalCredentials(paymentScript.replace("amount: '12.34'", "amount: getAmount()"), isPayment = true)
        }
    }


    @Test
    fun interpretsTheDeployedDescriptorExpiryAsUnixSeconds() {
        assertEquals(Instant.ofEpochSecond(1790014786), ItbWalletDriver.descriptorExpiry(JsonPrimitive(1790014786L)))
        assertFailsWith<IllegalArgumentException> { ItbWalletDriver.descriptorExpiry(JsonPrimitive("1790014786")) }
        assertFailsWith<IllegalArgumentException> { ItbWalletDriver.descriptorExpiry(JsonPrimitive(1.5)) }
    }

}
