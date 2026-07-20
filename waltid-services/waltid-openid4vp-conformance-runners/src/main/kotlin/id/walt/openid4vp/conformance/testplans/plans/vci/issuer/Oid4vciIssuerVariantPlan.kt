package id.walt.openid4vp.conformance.testplans.plans.vci.issuer

import id.walt.openid4vp.conformance.testplans.runner.req.IssuerTestPlanConfiguration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Generic OpenID4VCI issuer plan for the base oid4vci-1_0-issuer-test-plan.
 *
 * The conformance suite owns which test modules are applicable for a variant. This
 * class only supplies the selected variant and the local issuer/client configuration.
 */
class Oid4vciIssuerVariantPlan(
    private val issuerUrl: String,
    private val credentialConfigurationId: String,
    private val variant: IssuerVariant,
    private val clientAttestationIssuer: String,
    private val clientAttesterJwks: JsonObject,
    private val authorizationServer: String? = null,
    private val credentialProofTypeHint: String? = null,
    private val staticTxCode: String? = null,
) : IssuerTestPlan {

    // Client keys for DPoP and private_key_jwt tests.
    // language=JSON
    private val clientJwks = """
        {
            "keys": [
                {
                    "kty": "EC",
                    "crv": "P-256",
                    "alg": "ES256",
                    "use": "sig",
                    "kid": "conformance-test-key",
                    "d": "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ",
                    "x": "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM",
                    "y": "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"
                }
            ]
        }
    """.trimIndent()
    private val clientJwksJson = Json.decodeFromString<JsonObject>(clientJwks)
    // Client2 must use a different signing key for multi-client conformance modules.
    // language=JSON
    private val client2Jwks = """
        {
            "keys": [
                {
                    "kty": "EC",
                    "crv": "P-256",
                    "alg": "ES256",
                    "use": "sig",
                    "kid": "conformance-test-key-2",
                    "d": "jvakpzaRupzb8sCvDWyRwzPAAN7xF4Hsg0W845p55ec",
                    "x": "zVDfMnnJIr9cse46coCxNpv6iZ7ZaDhFDA0gCu-bxT0",
                    "y": "y9EGu_woCWPjcebrxuNRWJIXzfdIJMkvFMOXQ60DR50"
                }
            ]
        }
    """.trimIndent()
    private val client2JwksJson = Json.decodeFromString<JsonObject>(client2Jwks)
    private val moduleVariant = variant.toJsonObject().toString()

    override val config = IssuerTestPlanConfiguration(
        testPlanCreationUrl = {
            append("planName", "oid4vci-1_0-issuer-test-plan")
            append("variant", moduleVariant)
        },
        testPlanCreationConfiguration = kotlinx.serialization.json.buildJsonObject {
            putJsonObject("vci") {
                put("credential_issuer_url", issuerUrl)
                put("credential_configuration_id", credentialConfigurationId)
                put("client_attestation_issuer", clientAttestationIssuer)
                put("client_attester_keys_jwks", clientAttesterJwks)
                authorizationServer?.let { put("authorization_server", it) }
                credentialProofTypeHint?.let { put("credential_proof_type_hint", it) }
            }
            putJsonObject("client_attestation") {
                put("issuer", clientAttestationIssuer)
                put("attester_jwks", clientAttesterJwks)
            }
            putJsonObject("client") {
                put("client_id", "conformance-test-client")
                put("jwks", clientJwksJson)
            }
            putJsonObject("client2") {
                put("client_id", "conformance-test-client-2")
                put("jwks", client2JwksJson)
            }
            put("description", variant.description)
        },
        moduleVariant = moduleVariant,
        issuerUrl = issuerUrl,
        skippableModules = setOf("oid4vci-1_0-issuer-metadata-test-signed"),
        requiresPreAuthorizedOffer = variant.requiresCredentialOffer,
        credentialProfileId = deriveIssuerCredentialProfileId(credentialConfigurationId),
        staticTxCode = staticTxCode,
    )
}
