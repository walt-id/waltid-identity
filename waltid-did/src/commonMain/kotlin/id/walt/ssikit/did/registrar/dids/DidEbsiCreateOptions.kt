package id.walt.ssikit.did.registrar.dids

class DidEbsiCreateOptions(token: String) : DidCreateOptions(
    method = "ebsi",
    options = options(emptyMap(), secret = mapOf("token" to token))
)
