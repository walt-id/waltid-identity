package id.walt.openid4vp.conformance.testplans.plans.vci.issuer

import id.walt.openid4vp.conformance.testplans.runner.req.IssuerTestPlanConfiguration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * HAIP-compliant VCI Issuer Test Plan: SD-JWT VC + DPoP + private_key_jwt
 *
 * This profile targets HAIP (High Assurance Interoperability Profile) baseline requirements:
 * - DPoP (Demonstrating Proof-of-Possession) for token binding
 * - private_key_jwt client authentication (enterprise can upgrade to client_attestation)
 * - Authorization code flow
 * - SD-JWT VC credential format
 * - P-256 key curve (HAIP mandatory)
 * - SHA-256 hash algorithm (HAIP mandatory)
 *
 * Conformance test plan: oid4vci-1_0-issuer-haip-test-plan
 *
 * Note: This is the OSS-compatible HAIP baseline. Enterprise deployments requiring
 * client_attestation + key_attestation should modify client_auth_type accordingly.
 */
class Oid4vciIssuerSdJwtHaip(
    private val issuerUrl: String,
    private val credentialConfigurationId: String,
    private val clientAttestationIssuer: String = "https://issuer.test.attester.example",
    private val authorizationServer: String? = null,
    private val credentialProofTypeHint: String? = null,
) : IssuerTestPlan {

    // Client keys for DPoP (P-256 as required by HAIP)
    // language=JSON
    private val clientJwks = """
        {
            "keys": [
                {
                    "kty": "EC",
                    "crv": "P-256",
                    "alg": "ES256",
                    "use": "sig",
                    "kid": "conformance-haip-key",
                    "d": "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ",
                    "x": "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM",
                    "y": "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"
                }
            ]
        }
    """.trimIndent()
    private val clientJwksJson = Json.decodeFromString<JsonObject>(clientJwks)

    // Client attester keys for HAIP test configuration
    // language=JSON
    private val clientAttesterJwks = """
        {
            "keys": [
                {
                    "kty": "EC",
                    "d": "LdeE1GY68degnCA4f49mcjIOhWoubfeIs_KekzTeFm0",
                    "crv": "P-256",
                    "kid": "h0DQmZoTW_Qw4pNR5hszRscrSRaxB1anD9XCLyfh0lc",
                    "x": "qcYiEHtnh5ikzzycbk3Lfsi36b98KrSYdDbO9zPysK0",
                    "y": "9tJsFltI2Ddw95EI2mX9J7bu7UveP3vV280Buhiryyg"
                }
            ]
        }
    """.trimIndent()
    private val clientAttesterJwksJson = Json.decodeFromString<JsonObject>(clientAttesterJwks)

    // HAIP module variant:
    // - fapi_profile: vci_haip (HAIP-specific profile)
    // - sender_constrain: dpop (DPoP token binding)
    // - client_auth_type: private_key_jwt (OSS baseline; enterprise uses client_attestation)
    // - vci_authorization_code_flow_variant: wallet_initiated (standard authorization flow)
    // - authorization_request_type: simple (not PAR for this baseline)
    // - fapi_request_method: unsigned (request object not signed in this profile)
    // - vci_grant_type: authorization_code
    // - fapi_response_mode: plain_response
    // - credential_format: sd_jwt_vc
    private val moduleVariant = """
        {
          "fapi_profile": "vci_haip",
          "sender_constrain": "dpop",
          "client_auth_type": "private_key_jwt",
          "vci_authorization_code_flow_variant": "wallet_initiated",
          "authorization_request_type": "simple",
          "fapi_request_method": "unsigned",
          "vci_grant_type": "authorization_code",
          "fapi_response_mode": "plain_response",
          "credential_format": "sd_jwt_vc"
        }
    """.trimIndent()

    override val config = IssuerTestPlanConfiguration(
        testPlanCreationUrl = {
            append("planName", "oid4vci-1_0-issuer-haip-test-plan")
            append("variant", moduleVariant)
        },
        testPlanCreationConfiguration = buildJsonObject {
            putJsonObject("vci") {
                put("credential_issuer_url", issuerUrl)
                put("credential_configuration_id", credentialConfigurationId)
                put("client_attestation_issuer", clientAttestationIssuer)
                put("client_attester_keys_jwks", clientAttesterJwksJson)
                authorizationServer?.let { put("authorization_server", it) }
                credentialProofTypeHint?.let { put("credential_proof_type_hint", it) }
            }
            putJsonObject("client_attestation") {
                put("issuer", clientAttestationIssuer)
                put("attester_jwks", clientAttesterJwksJson)
            }
            putJsonObject("client") {
                put("client_id", "conformance-haip-issuer-client")
                put("jwks", clientJwksJson)
            }
            putJsonObject("client2") {
                put("client_id", "conformance-haip-issuer-client-2")
                put("jwks", clientJwksJson)
            }
            put("description", JsonPrimitive("OID4VCI 1.0 Issuer HAIP - SD-JWT VC + DPoP + private_key_jwt"))
        },
        moduleVariant = moduleVariant,
        issuerUrl = issuerUrl,
        skippableModules = setOf("oid4vci-1_0-issuer-metadata-test-signed"),
        requiresPreAuthorizedOffer = true,
        credentialProfileId = deriveProfileId(credentialConfigurationId)
    )

    /**
     * Derive the profile ID from the credential configuration ID.
     * Maps credential configuration IDs to issuer-api2 profile IDs.
     */
    private fun deriveProfileId(credentialConfigurationId: String): String {
        return when {
            credentialConfigurationId.contains("photoID_credential") -> "photoIdCredentialSdJwt"
            credentialConfigurationId.contains("org.iso.23220.photoid") -> "isoPhotoId"
            credentialConfigurationId.contains("org.iso.18013.5.1.mDL") -> "isoMdl"
            credentialConfigurationId.contains("urn:eu.europa.ec.eudi:por:1") -> "powerOfRepresentationSdJwt"
            credentialConfigurationId.contains("urn:eu.europa.ec.eudi:cor:1") -> "companyRegistrationSdJwt"
            credentialConfigurationId.contains("urn:eudi:pid:1") -> "eudiPidSdJwt"
            credentialConfigurationId.contains("eu.europa.ec.eudi.pid.1") -> "eudiPidMdoc"
            credentialConfigurationId.contains("identity_credential") -> "identityCredentialSdJwt"
            credentialConfigurationId.contains("eu.europa.ec.av.1") -> "euAgeVerificationMdoc"
            else -> throw IllegalArgumentException("Unknown credential configuration ID: $credentialConfigurationId")
        }
    }
}
