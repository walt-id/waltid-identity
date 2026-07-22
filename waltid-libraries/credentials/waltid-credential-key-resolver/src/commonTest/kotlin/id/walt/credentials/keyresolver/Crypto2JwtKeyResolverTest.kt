package id.walt.credentials.keyresolver

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.did.dids.resolver.Crypto2DidKeyResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class Crypto2JwtKeyResolverTest {
    @Test
    fun `DID key is selected by verification method ID`() = runTest {
        val resolver: Crypto2JwtVerificationKeyResolver = resolver("$DID#key-1", "$DID#key-2")

        val resolved = resolver.resolveFromJwt(
            jwtHeader = buildJsonObject { put("kid", "$DID#key-2") },
            jwtPayload = buildJsonObject { put("iss", DID) },
        )

        assertEquals(JwtKeyResolutionSource.DID, resolved?.source)
        assertEquals(KeyId("$DID#key-2"), resolved?.key?.id)
    }

    @Test
    fun `DID key is selected by verification method fragment`() = runTest {
        val resolved = resolver("$DID#key-1", "$DID#key-2").resolveFromJwt(
            jwtHeader = buildJsonObject { put("kid", "key-2") },
            jwtPayload = buildJsonObject { put("iss", DID) },
        )

        assertEquals(KeyId("$DID#key-2"), resolved?.key?.id)
    }

    @Test
    fun `DID with multiple keys is ambiguous without kid`() = runTest {
        val resolver = resolver("$DID#key-1", "$DID#key-2")

        assertNull(
            resolver.resolveFromJwt(
                jwtHeader = null,
                jwtPayload = buildJsonObject { put("iss", DID) },
            )
        )
    }

    @Test
    fun `DID resolution rejects wrong kid`() = runTest {
        val resolver = resolver("$DID#key-1")

        assertNull(
            resolver.resolveFromJwt(
                jwtHeader = buildJsonObject { put("kid", "$DID#missing") },
                jwtPayload = buildJsonObject { put("iss", DID) },
            )
        )
    }

    @Test
    fun `DID resolution rejects ambiguous fragment match`() = runTest {
        val resolver = resolver("$DID#shared", "did:example:other#shared")

        assertFailsWith<IllegalArgumentException> { resolver.resolveFromDid(DID, "shared") }
    }

    private suspend fun resolver(vararg keyIds: String): Crypto2JwtKeyResolver {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val keys = keyIds.map { keyId ->
            runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId(keyId),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )
            )
        }.toSet()
        return Crypto2JwtKeyResolver(didResolver = Crypto2DidKeyResolver { keys })
    }

    private companion object {
        const val DID = "did:example:issuer"
    }
}
