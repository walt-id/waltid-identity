package compatibility.v121

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.Crypto2DidKeyResolver
import id.walt.did.dids.resolver.DidDocumentCrypto2KeyResolver
import id.walt.did.dids.resolver.DidResolver
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Compile against the published v1.2.1 jars, then run against the current implementation. */
object ReleasedConsumer {
    @JvmStatic
    fun exercise(did: String, resolver: DidResolver): Int = runBlocking {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val defaults = DidDocumentCrypto2KeyResolver(resolver)
        val explicit = DidDocumentCrypto2KeyResolver(resolver, runtime)
        check(defaults.resolveToKeys(did).isNotEmpty())
        check(explicit.resolveToKeys(did).isNotEmpty())
        val bound = Crypto2DidKeyResolver { defaults.resolveToKeys(it) }
        val payload = buildJsonObject { put("iss", did) }
        check(Crypto2JwtKeyResolver().resolveFromJwt(null, payload) != null)
        check(Crypto2JwtKeyResolver(didResolver = bound).resolveFromJwt(null, payload) != null)
        check(Crypto2JwtKeyResolver(bound, runtime, false).resolveFromJwt(null, payload) != null)
        DidService.resolveToCrypto2Keys(did).getOrThrow().size
    }
}
