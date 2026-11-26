package id.walt.openid4vp.conformance

import com.nimbusds.jose.jwk.ECKey
import id.walt.openid4vp.conformance.testplans.keys.ClientAttestationTestAuthority
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.VciWalletHaip
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VciWalletHaipTest {
    @Test
    fun formatsSelectMatchingConfigurationsWithoutOverridingSuiteHaipDefaults() = runBlocking {
        val authority = ClientAttestationTestAuthority.create(clientId = "wallet-conformance-test")
        val plans = listOf("sd_jwt_vc", "mdoc").map { format ->
            VciWalletHaip("http://wallet", "http://adapter/credential-offer", "http://adapter/callback",
                "localhost", 8443, attestationAuthority = authority, credentialFormat = format)
        }
        for ((plan, id) in plans.zip(listOf("eu.europa.ec.eudi.pid.1", "eu.europa.ec.eudi.pid.mdoc.1"))) {
            assertEquals("oid4vci-1_0-wallet-haip-test-plan", plan.planName)
            assertEquals(mapOf("credential_format" to plan.credentialFormat,
                "vci_authorization_code_flow_variant" to "issuer_initiated",
                "vci_credential_offer_variant" to "by_value"), plan.variant)
            assertEquals("client_attestation", plan.clientAuthType)
            assertEquals(id, plan.configuration.getValue("vci").jsonObject.getValue("credential_configuration_id").jsonPrimitive.content)
            assertEquals(authority.trustAnchorPem, plan.configuration.getValue("client_attestation").jsonObject.getValue("trust_anchor").jsonPrimitive.content)
        }
        assertNotEquals(plans[0].producerId, plans[1].producerId)
        val credential = plans[1].configuration.getValue("credential").jsonObject
        val key = ECKey.parse(credential.getValue("signing_jwk").toString())
        val chain = credential.getValue("signing_jwk").jsonObject.getValue("x5c").jsonArray
        val factory = CertificateFactory.getInstance("X.509")
        val leaf = factory.generateCertificate(Base64.getDecoder().decode(chain.first().jsonPrimitive.content).inputStream()) as X509Certificate
        val root = factory.generateCertificate(credential.getValue("trust_anchor_pem").jsonPrimitive.content.byteInputStream()) as X509Certificate
        leaf.verify(root.publicKey)
        leaf.checkValidity()
        assertEquals(key.toECPublicKey(), leaf.publicKey)
        assertTrue(leaf.keyUsage[0])
        assertTrue("1.0.18013.5.1.2" in leaf.extendedKeyUsage)
    }

    @Test
    fun unsupportedCredentialFormatCannotSilentlyUseSdJwtConfiguration() = runBlocking {
        val authority = ClientAttestationTestAuthority.create(clientId = "wallet-conformance-test")
        assertFailsWith<IllegalArgumentException> {
            VciWalletHaip("http://wallet", "http://adapter/credential-offer", "http://adapter/callback",
                "localhost", 8443, attestationAuthority = authority, credentialFormat = "jwt_vc_json")
        }
    }
}
