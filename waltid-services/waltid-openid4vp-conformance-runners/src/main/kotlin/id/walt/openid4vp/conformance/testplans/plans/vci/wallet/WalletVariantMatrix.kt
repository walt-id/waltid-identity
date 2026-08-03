package id.walt.openid4vp.conformance.testplans.plans.vci.wallet

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path

/**
 * A suite-supported OpenID4VCI wallet test-plan context.
 *
 * Basic VCI variants are fully selected at plan creation. The HAIP plan fixes
 * most protocol parameters internally; its immediate/deferred/encrypted
 * modules are selected after the plan has been created.
 */
@Serializable
data class WalletVariant(
    val fapiProfile: String,
    val credentialFormat: String,
    val grantType: String,
    val authorizationCodeFlowVariant: String,
    val clientAuthType: String,
    val senderConstrain: String,
    val authorizationRequestType: String,
    val requestMethod: String,
    val credentialEncryption: String,
    val credentialIssuanceMode: String,
    val credentialOfferVariant: String? = null,
) {
    val isHaip: Boolean
        get() = fapiProfile == "vci_haip"

    val conformanceTestPlanName: String
        get() = if (isHaip) "oid4vci-1_0-wallet-haip-test-plan" else "oid4vci-1_0-wallet-test-plan"

    val id: String
        get() = listOfNotNull(
            fapiProfile.toIdPart(),
            credentialFormat.toIdPart(),
            grantType.toIdPart(),
            authorizationCodeFlowVariant.toIdPart(),
            clientAuthType.toIdPart(),
            senderConstrain.toIdPart(),
            authorizationRequestType.toIdPart(),
            requestMethod.toIdPart(),
            credentialIssuanceMode.toIdPart(),
            credentialEncryption.toIdPart(),
            credentialOfferVariant?.toIdPart(),
        ).joinToString("-")

    val description: String
        get() = listOfNotNull(
            "fapi_profile=$fapiProfile",
            "credential_format=$credentialFormat",
            "vci_grant_type=$grantType",
            "vci_authorization_code_flow_variant=$authorizationCodeFlowVariant",
            "client_auth_type=$clientAuthType",
            "sender_constrain=$senderConstrain",
            "authorization_request_type=$authorizationRequestType",
            "fapi_request_method=$requestMethod",
            "vci_credential_issuance_mode=$credentialIssuanceMode",
            "vci_credential_encryption=$credentialEncryption",
            credentialOfferVariant?.let { "vci_credential_offer_variant=$it" },
        ).joinToString(prefix = "OID4VCI 1.0 Wallet - ")

    fun toJsonObject(): JsonObject = buildJsonObject {
        put("fapi_profile", fapiProfile)
        put("credential_format", credentialFormat)
        put("vci_grant_type", grantType)
        put("vci_authorization_code_flow_variant", authorizationCodeFlowVariant)
        put("client_auth_type", clientAuthType)
        put("sender_constrain", senderConstrain)
        put("authorization_request_type", authorizationRequestType)
        put("fapi_request_method", requestMethod)
        put("vci_credential_issuance_mode", credentialIssuanceMode)
        put("vci_credential_encryption", credentialEncryption)
        credentialOfferVariant?.let { put("vci_credential_offer_variant", it) }
    }

    /** The HAIP plan accepts only its context selectors at plan creation. */
    fun testPlanCreationVariant(): JsonObject =
        if (!isHaip) {
            toJsonObject()
        } else {
            buildJsonObject {
                put("credential_format", credentialFormat)
                put("vci_authorization_code_flow_variant", authorizationCodeFlowVariant)
                credentialOfferVariant?.let { put("vci_credential_offer_variant", it) }
            }
        }

    private fun String.toIdPart(): String = when (this) {
        "vci_haip" -> "vcihaip"
        "sd_jwt_vc" -> "sdjwt"
        "authorization_code" -> "authcode"
        "pre_authorization_code" -> "preauth"
        "wallet_initiated" -> "wallet"
        "issuer_initiated" -> "issuer"
        "issuer_initiated_dc_api" -> "issuerdcapi"
        "private_key_jwt" -> "privatekeyjwt"
        "client_attestation" -> "clientatt"
        "signed_non_repudiation" -> "signednr"
        "by_value" -> "byvalue"
        "by_reference" -> "byreference"
        else -> replace("_", "")
    }
}
