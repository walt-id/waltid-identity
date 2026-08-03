package id.walt.openid4vp.conformance.testplans.plans.vci.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Path

/** Creates the suite configuration for one [WalletVariant]. */
class Oid4vciWalletVariantPlan(
    val variantContext: WalletVariant,
    private val adapterPublicUrl: String,
    private val clientId: String,
    private val clientAttestationIssuer: String,
    private val clientAttesterJwks: JsonObject,
    private val clientAttestationTrustAnchorPem: String,
    private val keyAttestationTrustAnchorPem: String,
    private val clientCertificatePem: String,
) : VciWalletTestPlan {
    override val description: String = variantContext.description
    override val planName: String = variantContext.conformanceTestPlanName
    override val variant: Map<String, String> = variantContext.toJsonObject()
        .mapValues { (_, value) -> value.toString().trim('"') }

    override val configuration: JsonObject = buildJsonObject {
        put("alias", "vci_wallet_${variantContext.id}")
        put("description", variantContext.description)
        putJsonObject("server") {
            putJsonObject("jwks") {
                put("keys", serverJwks["keys"]!!)
            }
        }
        putJsonObject("client") {
            put("client_id", clientId)
            put("redirect_uri", "$adapterPublicUrl/callback")
            put("certificate", clientCertificatePem)
            putJsonObject("jwks") {
                put("keys", clientJwks["keys"]!!)
            }
        }
        putJsonObject("credential") {
            put("trust_anchor_pem", credentialTrustAnchorPem)
            put("status_list_trust_anchor_pem", credentialTrustAnchorPem)
            put("signing_jwk", credentialSigningJwk)
        }
        putJsonObject("client_attestation") {
            put("issuer", clientAttestationIssuer)
            put("trust_anchor", clientAttestationTrustAnchorPem)
            put("attester_jwks", clientAttesterJwks)
            put("key_attestation_jwks", clientAttesterJwks)
            put("key_attestation_trust_anchor_pem", keyAttestationTrustAnchorPem)
        }
        putJsonObject("vci") {
            if (variantContext.authorizationCodeFlowVariant != "wallet_initiated") {
                put("credential_offer_endpoint", "$adapterPublicUrl/credential-offer")
            }
            // release-v5.1.x reads these legacy fields. Current suite releases
            // read the top-level client_attestation object above.
            if (variantContext.clientAuthType == "client_attestation") {
                put("client_attestation_issuer", clientAttestationIssuer)
                put("client_attestation_trust_anchor", clientAttestationTrustAnchorPem)
            }
            put(
                "credential_configuration_id",
                if (variantContext.credentialFormat == "mdoc") "eu.europa.ec.eudi.pid.mdoc.1" else "eu.europa.ec.eudi.pid.1"
            )
        }
        put("waitTimeoutSeconds", 120)
        put("maxWaitForNotificationSeconds", 20)
        put("publish", "no")
    }

    override val isHaip: Boolean
        get() = variantContext.isHaip

    override val credentialFormat: String
        get() = variantContext.credentialFormat

    override val grantType: String
        get() = variantContext.grantType

    override val senderConstraint: String
        get() = variantContext.senderConstrain

    override val clientAuthType: String
        get() = variantContext.clientAuthType

