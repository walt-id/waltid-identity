package id.walt.openid4vp.conformance.testplans.plans.vci.issuer

import id.walt.openid4vp.conformance.testplans.runner.req.IssuerTestPlanConfiguration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Phase-1 OpenID4VCI issuer conformance plan:
 * wallet-initiated + authorization-code + DPoP + client attestation.
 */
class Oid4vciIssuerClientAttestationDpop(
    private val issuerUrl: String,
    private val credentialConfigurationId: String,
    private val credentialFormat: CredentialFormatVariant,
    private val clientAttestationIssuer: String,
    private val clientAttesterJwks: JsonObject,
    private val authorizationServer: String? = null,
    private val credentialProofTypeHint: String? = null,
) : IssuerTestPlan {

    enum class CredentialFormatVariant(val variantValue: String, val metadataFormat: String) {
        SD_JWT_VC("sd_jwt_vc", "dc+sd-jwt"),
        MDOC("mdoc", "mso_mdoc")
    }

    // Client keys for DPoP
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
    // OSS configuration: Use private_key_jwt instead of client_attestation
    // For Enterprise: Change "private_key_jwt" to "client_attestation"
    private val moduleVariant = """
        {
          "fapi_profile": "vci",
          "sender_constrain": "dpop",
          "client_auth_type": "private_key_jwt",
          "vci_authorization_code_flow_variant": "wallet_initiated",
          "authorization_request_type": "simple",
          "fapi_request_method": "unsigned",
          "vci_grant_type": "authorization_code",
          "fapi_response_mode": "plain_response",
          "credential_format": "${credentialFormat.variantValue}"
        }
    """.trimIndent()

    override val config = IssuerTestPlanConfiguration(
        testPlanCreationUrl = {
            append("planName", "oid4vci-1_0-issuer-test-plan")
            append("variant", moduleVariant)
        },
        testPlanCreationConfiguration = buildJsonObject {
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
                put("jwks", clientJwksJson)
            }
            put("description", JsonPrimitive("OID4VCI 1.0 Issuer - ${credentialFormat.variantValue} - DPoP + Client Attestation"))
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
