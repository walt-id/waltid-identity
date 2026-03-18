package id.walt.openid4vci

/**
 * OAuth 2.0 (RFC 6749) response modes used when returning authorization responses.
 */
enum class ResponseMode(val value: String) {
    QUERY("query"),
    FRAGMENT("fragment"),
}
