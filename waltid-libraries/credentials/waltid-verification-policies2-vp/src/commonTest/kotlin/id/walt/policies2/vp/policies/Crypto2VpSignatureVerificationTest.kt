package id.walt.policies2.vp.policies

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.formats.SdJwtCredential
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.did.dids.DidService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Crypto2VpSignatureVerificationTest {
    @Test
    fun `W3C VP DID signature verifies through crypto2`() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", key).did
        val kid = DidService.resolveAuthenticationMethodId(did, key.getKeyId())
        val payload = JsonObject(mapOf("iss" to JsonPrimitive(did)))
        val jwt = key.signJws(
            Json.encodeToString(payload).encodeToByteArray(),
            mapOf("kid" to JsonPrimitive(kid)),
        )

        val verification = assertNotNull(verifyJwtVpWithCrypto2(jwt, payload))

        assertTrue(verification.result.isSuccess)
        assertEquals(did, (verification.result.getOrThrow() as JsonObject)["iss"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun `KB-JWT holder signature verifies through crypto2 and rejects tampering`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("holder"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val publicKey = runtime.restore(
            assertIs<EncodedKey.Jwk>(assertNotNull(key.capabilities.publicKeyExporter).exportPublicKey())
                .toStoredSoftwareKey(KeyId("holder-public"), setOf(KeyUsage.VERIFY))
        )
        val jwt = CompactJws.sign("{}".encodeToByteArray(), key, JwsAlgorithm.ED25519)
        val parts = jwt.split('.').toMutableList()
        parts[2] = (if (parts[2].first() == 'A') "B" else "A") + parts[2].drop(1)

        assertNotNull(publicKey.capabilities.verifier)
        assertTrue(publicKey.capabilities.signer == null)
        val allowedAlgorithms = setOf(JwsAlgorithm.ED25519)
        assertTrue(verifyKbJwtWithCrypto2(jwt, publicKey, allowedAlgorithms).isSuccess)
        assertTrue(verifyKbJwtWithCrypto2(parts.joinToString("."), publicKey, allowedAlgorithms).isFailure)
        assertTrue(verifyKbJwtWithCrypto2(jwt, publicKey, setOf(JwsAlgorithm.ES256)).isFailure)
    }

    @Test
    fun `KB-JWT signature policy uses credential crypto2 holder key`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("policy-holder"),
                spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val publicJwk = Jwk.parse(
            assertIs<EncodedKey.Jwk>(assertNotNull(key.capabilities.publicKeyExporter).exportPublicKey())
        )
        val credential = SdJwtCredential(
            dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
            credentialData = buildJsonObject {
                putJsonObject("cnf") { put("jwk", publicJwk) }
            },
            signature = null,
            signed = null,
        )
        val jwt = CompactJws.sign(
            payload = "{}".encodeToByteArray(),
            key = key,
            algorithm = JwsAlgorithm.ED25519,
            protectedHeader = buildJsonObject { put("typ", "kb+jwt") },
        )
        val presentation = DcSdJwtPresentation(
            sdJwt = "issuer.jwt",
            keyBindingJwt = jwt,
            credential = credential,
            audience = null,
            nonce = null,
            sdHash = null,
            presentationStringHashable = "issuer.jwt~",
        )
        val policy = KbJwtSignatureSdJwtVPPolicy(setOf(JwsAlgorithm.ED25519.identifier))

        assertTrue(policy.runPolicy(presentation, null).success)
        assertFalse(KbJwtSignatureSdJwtVPPolicy(setOf(JwsAlgorithm.ES256.identifier)).runPolicy(presentation, null).success)
        val genericEdDsaJwt = CompactJws.sign(
            payload = "{}".encodeToByteArray(),
            key = key,
            algorithm = JwsAlgorithm.EDDSA,
            protectedHeader = buildJsonObject { put("typ", "kb+jwt") },
        )
        assertFalse(KbJwtSignatureSdJwtVPPolicy().runPolicy(presentation.copy(keyBindingJwt = genericEdDsaJwt), null).success)

        val tamperedParts = jwt.split('.').toMutableList().apply {
            this[2] = (if (this[2].first() == 'A') "B" else "A") + this[2].drop(1)
        }
        assertFalse(policy.runPolicy(presentation.copy(keyBindingJwt = tamperedParts.joinToString(".")), null).success)
    }
}
