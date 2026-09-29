package id.walt.itb

import kotlin.test.*
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

class ItbPortalContractTest {
    private val script = """
        async function requestCredential() {
          const presentationRequestEndpoint = 'https://dev-i4mlab.aegean.gr/rfc-issuer/vp/dc-api/request';
          const validationSessionId = '00000000-0000-0000-0000-000000000001';
          const profile = 'pid-basic';
          const dcApiProtocol = 'openid4vp-v1-signed';
        }
    """.trimIndent()

    @Test
    fun readsTheDeployedScriptsSessionAndProtocolWithoutExecutingIt() {
        val request = ItbPortalBridge.parseDigitalCredentials(script)
        assertEquals("00000000-0000-0000-0000-000000000001", request.validationSession)
        assertEquals("pid-basic", request.profile)
        assertEquals("openid4vp-v1-signed", request.protocol)
        assertEquals("/rfc-issuer/vp/dc-api/request", request.endpoint.encodedPath)
    }

    @Test
    fun refusesAmbiguousOrChangedScriptInputs() {
        assertFailsWith<IllegalArgumentException> {
            ItbPortalBridge.parseDigitalCredentials(script + "\nconst validationSessionId = 'other-session';")
        }
        assertFailsWith<IllegalArgumentException> {
            ItbPortalBridge.parseDigitalCredentials(script.replace("const profile = 'pid-basic';", "const profile = resolveProfile();"))
        }
    }

    @Test
    fun preservesThePaymentFieldsFromTheTs12Script() {
        val paymentScript = """
            const requestEndpoint = 'https://dev-i4mlab.aegean.gr/rfc-issuer/vp/dc-api/request';
            const sessionId = 'payment-session';
            const profile = 'ts12-iban';
            const attestationType = 'sca-iban';
            const dcApiProtocol = 'openid4vp-v1-signed';
            const body = {
                merchant: 'Coffee Shop', payee_id: 'shop-42', currency: 'EUR',
                amount: '12.34', transaction_id: 'tx-dc-api-iban-001'
            };
        """.trimIndent()
        val request = ItbPortalBridge.parseDigitalCredentials(paymentScript)
        assertEquals("payment-session", request.validationSession)
        assertEquals("ts12-iban", request.profile)
        assertEquals(
            ItbWalletInteraction.Payment("sca-iban", "Coffee Shop", "shop-42", "EUR", "12.34", "tx-dc-api-iban-001"),
            request.payment,
        )
        assertFailsWith<IllegalArgumentException> {
            ItbPortalBridge.parseDigitalCredentials(paymentScript.replace("amount: '12.34'", "amount: getAmount()"))
        }
    }


    @Test
    fun interpretsTheDeployedDescriptorExpiryAsUnixSeconds() {
        assertEquals(Instant.ofEpochSecond(1790014786), ItbWalletDriver.descriptorExpiry(JsonPrimitive(1790014786L)))
        assertFailsWith<IllegalArgumentException> { ItbWalletDriver.descriptorExpiry(JsonPrimitive("1790014786")) }
        assertFailsWith<IllegalArgumentException> { ItbWalletDriver.descriptorExpiry(JsonPrimitive(1.5)) }
    }

}
