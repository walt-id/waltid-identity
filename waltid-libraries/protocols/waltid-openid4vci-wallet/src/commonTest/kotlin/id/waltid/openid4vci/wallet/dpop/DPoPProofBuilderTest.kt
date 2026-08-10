package id.waltid.openid4vci.wallet.dpop

import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class DPoPProofBuilderTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun omitsDefaultHttpsPort() {
        assertEquals(
            "https://issuer.example/token",
            DPoPProofBuilder().normalizedTargetUri("https://issuer.example:443/token"),
        )
    }

    @Test
    fun omitsImplicitHttpsPort() {
        assertEquals(
            "https://issuer.example/token",
            DPoPProofBuilder().normalizedTargetUri("https://issuer.example/token"),
        )
    }

    @Test
    fun omitsDefaultHttpPort() {
        assertEquals(
            "http://issuer.example/token",
            DPoPProofBuilder().normalizedTargetUri("http://issuer.example:80/token"),
        )
    }

    @Test
    fun preservesNonDefaultPort() {
        assertEquals(
            "https://issuer.example:8443/token",
            DPoPProofBuilder().normalizedTargetUri("https://issuer.example:8443/token"),
        )
    }

    @Test
    fun removesQueryAndFragment() {
        assertEquals(
            "https://issuer.example/token",
            DPoPProofBuilder().normalizedTargetUri(
                "https://issuer.example:443/token?secret=value#fragment",
            ),
        )
    }

    @Test
    fun normalizesEmptyPathToSlash() {
        assertEquals(
            "https://issuer.example/",
            DPoPProofBuilder().normalizedTargetUri("https://issuer.example:443"),
        )
    }

    @Test
    fun preservesIpv6AuthorityAndNonDefaultPort() {
        assertEquals(
            "https://[2001:db8::1]:8443/token",
            DPoPProofBuilder().normalizedTargetUri("https://[2001:db8::1]:8443/token"),
        )
    }

    @Test
    fun omitsDefaultPortFromIpv6Authority() {
        assertEquals(
            "https://[2001:db8::1]/token",
            DPoPProofBuilder().normalizedTargetUri("https://[2001:db8::1]:443/token"),
        )
    }

    @Test
    fun createsFreshBoundProofsWithoutQueryOrFragmentInHtu() = runTest {
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("dpop-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )
        val builder = DPoPProofBuilder()
        val first = builder.buildProof(
            key = key,
            httpMethod = "post",
            targetUri = "https://issuer.example:8443/token?secret=value#ignored",
            accessToken = "access-token",
            nonce = "server-nonce",
            supportedAlgorithms = setOf("ES256"),
        )
        val second = builder.buildProof(
            key = key,
            httpMethod = "POST",
            targetUri = "https://issuer.example:8443/token",
            accessToken = "access-token",
            supportedAlgorithms = setOf("ES256"),
        )

        val header = jwtPart(first, 0)
        val payload = jwtPart(first, 1)
        assertEquals("dpop+jwt", header["typ"]?.jsonPrimitive?.content)
        assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
        assertNotNull(header["jwk"])
        assertEquals("POST", payload["htm"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example:8443/token", payload["htu"]?.jsonPrimitive?.content)
        assertEquals("server-nonce", payload["nonce"]?.jsonPrimitive?.content)
        assertNotNull(payload["ath"])
        assertNotEquals(payload["jti"], jwtPart(second, 1)["jti"])
    }

    @Test
    fun rejectsUnsupportedHolderAlgorithm() = runTest {
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("dpop-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            DPoPProofBuilder().buildProof(
                key = key,
                httpMethod = "POST",
                targetUri = "https://issuer.example/token",
                supportedAlgorithms = setOf("EdDSA"),
            )
        }
    }

    @Test
    fun ed25519KeySelectsEdDsaWhenAdvertised() = runTest {
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("dpop-ed25519-eddsa"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )

        val proof = DPoPProofBuilder().buildProof(
            key = key,
            httpMethod = "POST",
            targetUri = "https://issuer.example/token",
            supportedAlgorithms = setOf("EdDSA"),
        )

        assertEquals("EdDSA", jwtPart(proof, 0)["alg"]?.jsonPrimitive?.content)
    }

    @Test
    fun ed25519KeySelectsEd25519WhenAdvertised() = runTest {
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("dpop-ed25519-ed25519"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            ),
        )

        val proof = DPoPProofBuilder().buildProof(
            key = key,
            httpMethod = "POST",
            targetUri = "https://issuer.example/token",
            supportedAlgorithms = setOf("Ed25519"),
        )

        assertEquals("Ed25519", jwtPart(proof, 0)["alg"]?.jsonPrimitive?.content)
    }

    private fun jwtPart(jwt: String, index: Int) =
        Json.parseToJsonElement(jwt.split('.')[index].decodeFromBase64Url().decodeToString()).jsonObject
}
