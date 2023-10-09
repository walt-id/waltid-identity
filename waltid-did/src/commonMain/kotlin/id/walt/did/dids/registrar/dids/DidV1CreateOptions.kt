package id.walt.did.dids.registrar.dids

import id.walt.core.crypto.keys.KeyType

class DidV1CreateOptions(ledger: String = "test", keyType: KeyType) : DidCreateOptions(
    method = "v1",
    options = options("ledger" to ledger, "keytype" to keyType.name.lowercase())
)
