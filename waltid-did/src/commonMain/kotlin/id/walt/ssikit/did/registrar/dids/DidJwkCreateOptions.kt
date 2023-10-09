package id.walt.ssikit.did.registrar.dids

import id.walt.core.crypto.keys.KeyType

class DidJwkCreateOptions(keyType: KeyType = KeyType.Ed25519) : DidCreateOptions(
    method = "jwk",
    options = options("keyType" to keyType.name.lowercase())
)
