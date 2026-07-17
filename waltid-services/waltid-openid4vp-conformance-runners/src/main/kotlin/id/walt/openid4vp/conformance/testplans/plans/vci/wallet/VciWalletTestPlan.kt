package id.walt.openid4vp.conformance.testplans.plans.vci.wallet

import kotlinx.serialization.json.JsonObject

/**
 * Base interface for VCI wallet-side conformance test plans.
 *
 * Unlike VP wallet tests (where conformance suite is the verifier),
 * VCI wallet tests have the conformance suite act as the **credential issuer**.
 *
 * The wallet (our implementation) must:
 * 1. Discover issuer metadata
 * 2. Handle authorization (auth code or pre-auth)
 * 3. Request credentials with proper proofs (DPoP, etc.)
 * 4. Handle deferred issuance if needed
 *
 * ## Implementation
 *
 * Implementations provide:
 * - Variant parameters (credential format, grant type, etc.)
 * - Configuration JSON for conformance suite
 * - Optional overrides for plan name and other properties
 *
 * @see VciWalletSdJwtDpop for SD-JWT VC + DPoP implementation
 */
interface VciWalletTestPlan {

    /** Human-readable description of this test plan */
    val description: String

    /** OpenID4VCI test plan name on conformance suite */
    val planName: String
        get() = "oid4vci-1_0-wallet-test-plan"

    /** Test plan variant parameters */
    val variant: Map<String, String>

    /** Test plan configuration JSON for conformance suite */
    val configuration: JsonObject

    // ─────────────────────────────────────────────────────────────────────────────
    // Derived Properties (from variant)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Credential format: sd_jwt_vc or mdoc */
    val credentialFormat: String
        get() = variant["credential_format"] ?: "sd_jwt_vc"

    /** Grant type: authorization_code or pre_authorization_code */
    val grantType: String
        get() = variant["vci_grant_type"] ?: "authorization_code"

    /** Sender constraint: dpop or mtls */
    val senderConstraint: String
        get() = variant["sender_constrain"] ?: "dpop"

    /** Client authentication type: private_key_jwt, client_attestation, etc. */
    val clientAuthType: String
        get() = variant["client_auth_type"] ?: "private_key_jwt"

    /** Whether this is a HAIP profile */
    val isHaip: Boolean
        get() = variant["fapi_profile"] == "vci_haip"

    /** Credential encryption: plain or encrypted */
    val credentialEncryption: String
        get() = variant["vci_credential_encryption"] ?: "plain"

    /** Credential issuance mode: immediate or deferred */
    val issuanceMode: String
        get() = variant["vci_credential_issuance_mode"] ?: "immediate"

    /** Authorization flow variant: wallet_initiated or issuer_initiated */
    val authFlowVariant: String
        get() = variant["vci_authorization_code_flow_variant"] ?: "wallet_initiated"

    /** Credential offer variant: by_value or by_reference */
    val credentialOfferVariant: String
        get() = variant["vci_credential_offer_variant"] ?: "by_value"
}
