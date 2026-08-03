package id.waltid.openid4vp.wallet.request

/** Policy for handling unsecured (`alg=none`) OpenID4VP Request Objects. */
enum class UnsignedRequestObjectPolicy {
    /** Allow unsecured requests for testing or legacy interoperability. */
    ALLOW_UNSIGNED,

    /** Require every Request Object to be signed. */
    REQUIRE_SIGNED,
}
