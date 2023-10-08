package id.walt.ssikit.did.registrar.dids

import id.walt.core.crypto.keys.KeyType

class DidKeyCreateOptions(keyType: KeyType, useJwkJcsPub: Boolean = false) : DidCreateOptions(
    method = "key",
    options = options("keyType" to keyType.name.lowercase(), "useJwkJcsPub" to useJwkJcsPub)
)
