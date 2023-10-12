package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.KeyType

class DidWebCreateOptions(domain: String, path: String = "", keyType: KeyType = KeyType.Ed25519) : DidCreateOptions(
    method = "web",
    options = options("domain" to domain, "path" to path, "keyType" to keyType)
)
