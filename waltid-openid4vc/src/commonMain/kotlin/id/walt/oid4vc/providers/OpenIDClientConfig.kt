package id.walt.oid4vc.providers

data class OpenIDClientConfig(
    val clientID: String,
    val clientSecret: String?,
    val redirectUri: String?,
    val useCodeChallenge: Boolean = false
)
