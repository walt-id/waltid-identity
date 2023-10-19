package id.walt.did.dids.resolver.local

import id.walt.did.dids.document.DidDocument

abstract class LocalResolverMethod(val method: String) {

    abstract suspend fun resolve(did: String): Result<DidDocument>
    abstract suspend fun resolveToKey(did: String): Result<id.walt.crypto.keys.Key>

}
