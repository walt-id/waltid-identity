package id.walt.openid4vci.responses

/** Shared cache protection for OpenID4VCI HTTP responses, independent of the server framework. */
object OpenId4VciResponseHeaders {
    val NO_STORE: Map<String, String> = mapOf("Cache-Control" to "no-store")
    val NO_STORE_WITH_PRAGMA: Map<String, String> = NO_STORE + ("Pragma" to "no-cache")
}
