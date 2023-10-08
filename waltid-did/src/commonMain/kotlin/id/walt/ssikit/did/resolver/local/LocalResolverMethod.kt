package id.walt.ssikit.did.resolver.local

import id.walt.core.crypto.keys.Key
import id.walt.ssikit.did.document.DidDocument

abstract class LocalResolverMethod(val method: String) {

    abstract suspend fun resolve(did: String): Result<DidDocument>
    abstract suspend fun resolveToKey(did: String): Result<Key>

}
