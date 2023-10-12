package id.walt.did.dids.registrar.dids

class DidEbsiCreateOptions(version: Int, token: String) : DidCreateOptions(
    method = "ebsi",
    options = options(options = mapOf("version" to version), secret = mapOf("token" to token))
)
