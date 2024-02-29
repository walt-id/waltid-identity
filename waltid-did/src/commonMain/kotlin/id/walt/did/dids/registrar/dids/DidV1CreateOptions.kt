package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.KeyType
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
class DidV1CreateOptions(ledger: String = "test", keyType: KeyType) : DidCreateOptions(
    method = "v1",
    options = options("ledger" to ledger, "keytype" to keyType.name.lowercase())
)
