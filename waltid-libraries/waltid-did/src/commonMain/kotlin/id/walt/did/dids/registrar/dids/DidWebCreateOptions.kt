package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.KeyType
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidWebCreateOptions(domain: String, path: String = "", keyType: KeyType = KeyType.Ed25519) : DidCreateOptions(
    method = "web",
    config = config("domain" to domain, "path" to path, "keyType" to keyType)
)
