import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import id.walt.did.dids.resolver.local.DidJwkResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalDidJwkConsistencyTest {

    private val localResolver = DidJwkResolver()
    private val localRegistrar = DidJwkRegistrar()

    @Test
    fun testDidJwkCreateAndResolveConsistency() = runTest{
        val keyList: List<JWKKey> = KeyType.entries.map { JWKKey.generate(it) }
        for (key in keyList) {
            val didResult = localRegistrar.registerByKey(key, DidJwkCreateOptions(keyType = key.keyType))
            val resolvedKey = localResolver.resolveToKey(didResult.did).getOrThrow()
            assertEquals(key.getThumbprint(),resolvedKey.getThumbprint())
        }
    }
}