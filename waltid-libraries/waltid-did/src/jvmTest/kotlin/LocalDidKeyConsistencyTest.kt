import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.dids.resolver.local.DidKeyResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalDidKeyConsistencyTest {

    private val localResolver = DidKeyResolver()
    private val localRegistrar = DidKeyRegistrar()

    @Test
    fun testDidKeyCreateAndResolveConsistency() = runTest {
        val keyList: List<JWKKey> = KeyType.entries.map { JWKKey.generate(it) }
        for (key in keyList) {
            val didResult = localRegistrar.registerByKey(key, DidKeyCreateOptions(keyType = key.keyType))
            val resolvedKey = localResolver.resolveToKey(didResult.did).getOrThrow()
            assertEquals(key.getThumbprint(), resolvedKey.getThumbprint())
        }
    }

    @Test
    fun testDidKeyCreateAndResolveJwkJcsPubConsistency() = runTest {
        val keyList: List<JWKKey> = KeyType.entries.map { JWKKey.generate(it) }
        for (key in keyList) {
            val didResult = localRegistrar.registerByKey(key, DidKeyCreateOptions(keyType = key.keyType, useJwkJcsPub = true))
            localResolver.resolveToKey(didResult.did)
            val resolvedKey = localResolver.resolveToKey(didResult.did).getOrThrow()
            assertEquals(key.getThumbprint(), resolvedKey.getThumbprint())
        }
    }
}
