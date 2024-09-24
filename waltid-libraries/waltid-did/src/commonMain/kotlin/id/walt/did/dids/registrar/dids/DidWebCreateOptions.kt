package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.KeyType
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Class encompassing the set of parameters that are involved in the creation of [`did:web`](https://w3c-ccg.github.io/did-method-web/) DIDs via the [id.walt.did.dids.registrar.local.web.DidWebRegistrar] class.
 * @property domain The web domain name (cannot be empty).
 * @property path Optional URL path parameter.
 * @property keyType The type of [id.walt.crypto.keys.Key] that will be generated. This parameter is used in the context of the [id.walt.did.dids.registrar.local.web.DidWebRegistrar.register] function and is ignored when the [didDocConfig] property is specified.
 * @property didDocConfig Optional parameter that, in short, provides users with more control over the did document that will be produced. Refer to [DidDocConfig] for more information. This parameter, when specified, takes precedence in the [id.walt.did.dids.registrar.local.web.DidWebRegistrar.register] function over the `keyType` property.
 * @see [id.walt.did.dids.registrar.local.web.DidWebRegistrar]
 * @see [DidDocConfig]
 * @see [KeyType]
 * @see [id.walt.crypto.keys.Key]
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class DidWebCreateOptions(
    domain: String,
    path: String = "",
    keyType: KeyType = KeyType.Ed25519,
    didDocConfig: DidDocConfig? = null,
) : DidCreateOptions(
    method = "web",
    config = config("domain" to domain, "path" to path, "keyType" to keyType),
    didDocConfig = didDocConfig,
)
