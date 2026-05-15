package id.walt.wallet2.data

/**
 * Events emitted during the lifecycle of a wallet operation session.
 * Used as the discriminator in session-update notifications (SSE, webhooks).
 *
 * Mirrors the pattern of SessionEvent in waltid-openid4vp-verifier.
 */
enum class WalletSessionEvent {
    // --- Issuance (OpenID4VCI) ---
    /** Credential offer has been resolved and metadata fetched. */
    issuance_offer_resolved,
    /** Token has been obtained from the authorization server. */
    issuance_token_obtained,
    /** Proof of possession has been signed. */
    issuance_proof_signed,
    /** Credential(s) received from the issuer. */
    issuance_credential_received,
    /** Credential issuance was deferred — issuer accepted but will issue credential later. */
    issuance_deferred,

    /** Credential(s) stored in the credential store. */
    issuance_credential_stored,
    /** Issuance flow completed successfully. */
    issuance_completed,
    /** Issuance flow failed. */
    issuance_failed,

    // --- Presentation (OpenID4VP) ---
    /** Authorization request has been parsed. */
    presentation_request_parsed,
    /** Credentials have been selected for presentation. */
    presentation_credentials_selected,
    /** Presentation(s) have been signed / constructed. */
    presentation_signed,
    /** Presentation has been submitted to the verifier. */
    presentation_submitted,
    /** Presentation flow completed successfully. */
    presentation_completed,
    /** Presentation flow failed. */
    presentation_failed,
}
