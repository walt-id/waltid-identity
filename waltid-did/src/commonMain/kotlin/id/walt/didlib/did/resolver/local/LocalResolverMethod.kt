package id.walt.didlib.did.resolver.local

import id.walt.core.crypto.keys.Key
import id.walt.didlib.did.document.DidDocument

abstract class LocalResolverMethod(val method: String) {

    abstract suspend fun resolve(did: String): Result<DidDocument>
    abstract suspend fun resolveToKey(did: String): Result<Key>

}
