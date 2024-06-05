package id.walt.did.localresolvers

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.resolver.local.LocalResolverMethod
import kotlin.test.assertTrue

object LocalResolverTestUtils {
    suspend fun testDids(resolver: LocalResolverMethod, vararg dids: String, expectedKeyType: KeyType): Map<String, Result<Key>> {
        val results = dids.associateWith { did -> resolver.resolveToKey(did) }
        results.forEach { (did, result) ->
            println("$did -> $result")
        }
        assertTrue { results.all { it.value.isSuccess } }

        val keyTypes = results.map { it.value.getOrThrow().keyType }
        assertTrue { keyTypes.all { it == expectedKeyType } }

        return results


    }

}
