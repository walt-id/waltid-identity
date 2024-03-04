package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.KeyType
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * The did:key implementation defaults to the W3C CCG spec https://w3c-ccg.github.io/did-method-key/. When
 * _useJwkJcsPub_ is set to `true` the EBSI implementation (jwk_jcs-pub encoding) according
 * https://hub.ebsi.eu/tools/libraries/key-did-resolver is performed.
 */
@ExperimentalJsExport
@JsExport
class DidKeyCreateOptions(keyType: KeyType = KeyType.Ed25519, useJwkJcsPub: Boolean = false) : DidCreateOptions(
    method = "key",
    options = options("keyType" to keyType.name.lowercase(), "useJwkJcsPub" to useJwkJcsPub)
)
