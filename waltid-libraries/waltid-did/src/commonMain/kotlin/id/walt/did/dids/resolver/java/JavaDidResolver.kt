package id.walt.did.dids.resolver.java

import id.walt.crypto.keys.Key
import id.walt.did.dids.resolver.DidResolver
import kotlinx.serialization.json.JsonObject

abstract class JavaDidResolver : DidResolver {

    abstract fun javaGetSupportedMethods(): Set<String>
    override suspend fun getSupportedMethods(): Result<Set<String>> =
        runCatching { javaGetSupportedMethods() }

    abstract fun javaResolve(): JsonObject
    override suspend fun resolve(did: String) =
        runCatching { javaResolve() }

    abstract fun javaResolveToKey(): Key
    @Deprecated("Use Crypto2DidKeyResolver or Crypto2DidService for key resolution")
    override suspend fun resolveToKey(did: String) = runCatching {
        javaResolveToKey()
    }
}
