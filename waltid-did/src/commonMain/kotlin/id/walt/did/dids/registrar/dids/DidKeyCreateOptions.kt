package id.walt.did.dids.registrar.dids

import id.walt.core.crypto.keys.KeyType

class DidKeyCreateOptions(keyType: KeyType = KeyType.Ed25519, useJwkJcsPub: Boolean = false) : DidCreateOptions(
    method = "key",
    options = options("keyType" to keyType.name.lowercase(), "useJwkJcsPub" to useJwkJcsPub)
)
