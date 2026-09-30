package id.walt.openid4vp.conformance

import com.nimbusds.jose.jwk.ECKey
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import id.walt.openid4vp.conformance.testplans.*
import id.walt.openid4vp.conformance.testplans.plans.vci.issuer.*
import kotlinx.serialization.json.*
import kotlin.test.*

class IssuerKeyAttestationTest {
    private val attesterText = requireNotNull(javaClass.getResource("/keys/attester-key.json")).readText()
    private val jwks = parseKeyAttesterJwks(attesterText)

    @Test
    fun acceptsJwkAndJwksAndPreservesCertificateChain() {
        assertEquals(jwks, parseKeyAttesterJwks(jwks.toString()))
        val key = jwks.getValue("keys").jsonArray.single().jsonObject
        assertEquals(Json.parseToJsonElement(attesterText).jsonObject["x5c"], key["x5c"])
        assertEquals("ES256", key.getValue("alg").jsonPrimitive.content)
        assertFailsWith<IllegalArgumentException> { parseKeyAttesterJwks("{\"keys\":[]}") }
        assertFailsWith<IllegalArgumentException> {
            parseKeyAttesterJwks(ECKey.parse(attesterText).toPublicJWK().toJSONString())
        }
    }

    @Test
    fun sendsKeyAttesterSeparatelyFromClientAttesterOnlyWhenConfigured() {
        val clientKeys = buildJsonObject { put("keys", JsonArray(emptyList())) }
        fun plan(keyKeys: JsonObject?) = Oid4vciIssuerVariantPlan(
            issuerUrl = "https://issuer.example/openid4vci",
            credentialConfigurationId = "identity_credential",
            variant = IssuerVariantMatrix.base().first(),
            clientAttestationIssuer = "https://client.example",
            clientAttesterJwks = clientKeys,
            keyAttesterJwks = keyKeys,
        ).config.testPlanCreationConfiguration.getValue("client_attestation").jsonObject
        assertFalse("key_attestation_jwks" in plan(null))
        val configured = plan(jwks)
        assertEquals(clientKeys, configured["attester_jwks"])
        assertEquals(jwks, configured["key_attestation_jwks"])
    }

    @Test
    fun issuerFixturesRequireAttestationAndTrustTheMatchingPublicKey() {
        // Issuer2's loader parses a stream; relative includes cannot rely on a file URL.
        fun fixture(name: String) = ConfigFactory.parseString(
            requireNotNull(javaClass.getResource("/issuer2/$name")).readText()
        ).resolve()
        val service = fixture("issuer-service-key-attestation.conf")
        val publicKey = ECKey.parse(service.getConfig("keyAttestationConfig.verificationMethod.jwk")
            .root().render(ConfigRenderOptions.concise()))
        assertFalse(publicKey.isPrivate)
        assertEquals(ECKey.parse(attesterText).computeThumbprint(), publicKey.computeThumbprint())
        val catalog = fixture("credential-issuer-metadata-key-attestation.conf").getObject("credentialConfigurations")
        assertEquals(4, catalog.size)
        catalog.values.forEach {
            assertTrue((it as com.typesafe.config.ConfigObject).toConfig()
                .hasPath("proof_types_supported.jwt.key_attestations_required"))
        }
    }

    private fun passingResult() = IssuerVariantRunResult(
        variantId = "key-attestation",
        variant = JsonObject(emptyMap()),
        status = IssuerVariantRunStatus.PASSED,
        modules = keyAttestationAcceptanceModules.map {
            IssuerVariantModuleRunResult(it, testId = "test-$it", status = "FINISHED", result = "PASSED", accepted = true)
        },
    )

    @Test
    fun acceptanceRequiresBothExecutedTestsForEveryVariant() {
        val passing = passingResult()
        requireExecutedKeyAttestation(listOf(passing))
        assertFailsWith<IllegalArgumentException> { requireExecutedKeyAttestation(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            requireExecutedKeyAttestation(listOf(passing.copy(modules = passing.modules.take(1))))
        }
        for (badModule in listOf(
            passing.modules.last().copy(result = "SKIPPED"),
            passing.modules.last().copy(result = "FAILED"),
            passing.modules.last().copy(testId = null),
            passing.modules.last().copy(status = "WAITING"),
        )) {
            assertFailsWith<IllegalArgumentException> {
                requireExecutedKeyAttestation(listOf(passing, passing.copy(modules = listOf(passing.modules.first(), badModule))))
            }
        }
    }

    @Test
    fun encryptedHaipOnlyAllowsAbsentNegativeTestAndStillRequiresHappyFlow() {
        val passing = passingResult()
        val encryptedHaip = passing.copy(
            variantId = "haip-encrypted",
            variant = buildJsonObject {
                put("fapi_profile", "vci_haip")
                put("vci_grant_type", "authorization_code")
                put("vci_credential_encryption", "encrypted")
            },
            modules = passing.modules.take(1),
        )
        requireExecutedKeyAttestation(listOf(passing, encryptedHaip))
        // An encrypted-only run must not claim negative coverage.
        assertFailsWith<IllegalArgumentException> { requireExecutedKeyAttestation(listOf(encryptedHaip)) }
        assertFailsWith<IllegalArgumentException> {
            requireExecutedKeyAttestation(listOf(passing, encryptedHaip.copy(modules = emptyList())))
        }
        for (result in listOf("SKIPPED", "FAILED")) {
            assertFailsWith<IllegalArgumentException> {
                requireExecutedKeyAttestation(listOf(passing, encryptedHaip.copy(
                    modules = listOf(passing.modules.first(), passing.modules.last().copy(result = result)),
                )))
            }
        }
        // Neither plain HAIP nor encrypted Basic VCI gets the absence exception.
        for ((field, value) in listOf("vci_credential_encryption" to "plain", "fapi_profile" to "vci")) {
            assertFailsWith<IllegalArgumentException> {
                requireExecutedKeyAttestation(listOf(passing, encryptedHaip.copy(
                    variant = JsonObject(encryptedHaip.variant + (field to JsonPrimitive(value))),
                )))
            }
        }
    }
}
