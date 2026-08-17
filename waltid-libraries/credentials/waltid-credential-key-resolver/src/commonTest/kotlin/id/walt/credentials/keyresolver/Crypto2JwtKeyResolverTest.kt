package id.walt.credentials.keyresolver

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
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

    @Test
    fun `did jwk key is selected by method verification fragment`() = runTest {
        val resolved = resolver("$DID_JWK#0").resolveFromJwt(
            jwtHeader = buildJsonObject { put("kid", "$DID_JWK#0") },
            jwtPayload = buildJsonObject { put("iss", DID_JWK) },
        )

        assertEquals(JwtKeyResolutionSource.DID, resolved?.source)
        assertEquals(KeyId("$DID_JWK#0"), resolved?.key?.id)
    }

    @Test
    fun `did jwk key is selected when kid is the DID`() = runTest {
        val resolved = resolver("$DID_JWK#0").resolveFromJwt(
            jwtHeader = buildJsonObject { put("kid", DID_JWK) },
            jwtPayload = buildJsonObject { put("iss", DID_JWK) },
        )

        assertEquals(KeyId("$DID_JWK#0"), resolved?.key?.id)
    }

    @Test
    fun `did jwk single key is used when kid is a kms resource path`() = runTest {
        val resolved = resolver("$DID_JWK#0").resolveFromJwt(
            jwtHeader = buildJsonObject { put("kid", "$DID_JWK#org.tenant.kms.key_issuer") },
            jwtPayload = buildJsonObject { put("iss", DID_JWK) },
        )

        assertEquals(KeyId("$DID_JWK#0"), resolved?.key?.id)
    }

    @Test
    fun `did jwk single key is used when kid is omitted`() = runTest {
        val resolved = resolver("$DID_JWK#0").resolveFromJwt(
            jwtHeader = null,
            jwtPayload = buildJsonObject { put("iss", DID_JWK) },
        )

        assertEquals(KeyId("$DID_JWK#0"), resolved?.key?.id)
    }

    private suspend fun resolver(vararg keyIds: String): Crypto2JwtKeyResolver {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
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
        const val DID_JWK =
            "did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2IiwieCI6ImVCVkJMaC16eVQ0NWZTbklWUTBtbmVvTUc0dTU4eHFhSHk1aE5SOVZPdGciLCJ5IjoiRTl0ZTdyX3A5dWIyaS1ER01SUzVwSVQ2ZWpzdDd4OTRkLTBFTWoteVEzbyJ9"
    }
}
