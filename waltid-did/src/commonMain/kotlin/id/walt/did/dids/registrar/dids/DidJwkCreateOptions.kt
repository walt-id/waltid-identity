package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.KeyType

class DidJwkCreateOptions(keyType: KeyType = KeyType.Ed25519) : DidCreateOptions(
    method = "jwk",
    options = options("keyType" to keyType.name.lowercase())
)
