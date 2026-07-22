package id.walt.credentials.formats

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto.utils.MultiBaseUtils
import id.walt.crypto.utils.MultiCodecUtils
import id.walt.crypto.utils.ShaUtils
import id.walt.did.dids.DidService
import id.walt.did.utils.JsonCanonicalization
import id.walt.credentials.signatures.JwtCredentialSignature
import id.walt.credentials.signatures.SdJwtCredentialSignature
import id.walt.w3c.vc.vcs.W3CVC
import id.walt.x509.GenericX509CertificateBuilder
import id.walt.x509.GenericX509CertificateProfileData
import id.walt.x509.X509DistinguishedName
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Crypto2DigitalCredentialTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `W3C credential secured with SD-JWT uses dc+sd-jwt presentation format`() {
        val signature = SdJwtCredentialSignature("issuer.jwt~", JsonObject(emptyMap()))
        val credentialData = buildJsonObject { put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential")))) }

        assertEquals("dc+sd-jwt", W3C11(credentialData = credentialData, signature = signature, signed = "issuer.jwt~").format)
        assertEquals("dc+sd-jwt", W3C2(credentialData = credentialData, signature = signature, signed = "issuer.jwt~").format)
    }

    @Test
    fun `W3C credential resolves x5c signer and verifies with crypto2`() = runTest {
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("credential-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val certificate = GenericX509CertificateBuilder().buildDer(
            profileData = GenericX509CertificateProfileData(
                subjectName = X509DistinguishedName(commonName = "Credential issuer"),
            ),
            subjectPublicKey = key,
            signingKey = key,
            signatureAlgorithm = SignatureAlgorithm.Ecdsa(
                DigestAlgorithm.SHA_256,
                EcdsaSignatureEncoding.DER,
            ),
        )
        val credential = W3CVC.build(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential", "ExampleCredential"),
            "credentialSubject" to buildJsonObject { put("id", "did:example:holder") },
        )
        val signed = credential.signJws(
            issuerKey = key,
            algorithm = JwsAlgorithm.ES256,
            issuerId = "https://issuer.example",
            subjectDid = "did:example:holder",
            additionalJwtHeader = mapOf(
                "x5c" to JsonArray(
                    listOf(JsonPrimitive(Base64.Default.encode(certificate.bytes.toByteArray())))
                )
            ),
        )
        val decoded = CompactJws.decodeUnverified(signed)
        val payload = Json.parseToJsonElement(decoded.payload.decodeToString()) as JsonObject
        val digitalCredential: DigitalCredential = W3C11(
            credentialData = payload,
            signature = JwtCredentialSignature(signed, decoded.protectedHeader),
            signed = signed,
        )

        val signer = assertNotNull(digitalCredential.getSignerCrypto2Key())
        assertEquals(KeySpec.Ec(EcCurve.P256), signer.spec)
        assertTrue(
            digitalCredential.verifyCrypto2(
                signer,
                Crypto2VerificationAlgorithms(jws = setOf(JwsAlgorithm.ES256)),
            ).isSuccess
        )
    }

    @Test
    fun `W3C and SD-JWT holder JWKs restore as public verification-only keys`() = runTest {
        val key = signingKey("holder-key")
        val publicJwk = Jwk.parse(
            assertIs<EncodedKey.Jwk>(requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey())
        )

        holderCredentials(publicJwk).forEach { credential ->
            val holderKey = assertNotNull(credential.getHolderCrypto2Key())
            assertEquals(KeySpec.Ec(EcCurve.P256), holderKey.spec)
            assertNotNull(holderKey.capabilities.verifier)
            assertNull(holderKey.capabilities.signer)
            assertNull(holderKey.capabilities.privateKeyExporter)
        }
    }

    @Test
    fun `holder key resolution rejects private JWKs and returns null when missing`() = runTest {
        val key = signingKey("private-holder-key")
        val privateJwk = Jwk.parse(
            assertIs<EncodedKey.Jwk>(requireNotNull(key.capabilities.privateKeyExporter).exportPrivateKey())
        )

        holderCredentials(privateJwk).forEach { credential ->
            assertFailsWith<IllegalArgumentException> { credential.getHolderCrypto2Key() }
        }
        holderCredentials().forEach { credential ->
            assertNull(credential.getHolderCrypto2Key())
        }
    }

    @Test
    fun `SD-JWT presentation verifies holder signature with crypto2 key`() = runTest {
        val key = signingKey("presentation-holder-key")
        val publicJwk = Jwk.parse(
            assertIs<EncodedKey.Jwk>(requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey())
        )
        val hashablePresentation = "issuer.jwt~"
        val keyBindingPayload = buildJsonObject {
            put("aud", "verifier")
            put("nonce", "nonce")
            put("sd_hash", ShaUtils.calculateSha256Base64Url(hashablePresentation))
        }
        val keyBindingJwt = CompactJws.sign(
            payload = Json.encodeToString(keyBindingPayload).encodeToByteArray(),
            key = key,
            algorithm = JwsAlgorithm.ES256,
        )
        val presentation = DcSdJwtPresentation(
            sdJwt = "issuer.jwt",
            keyBindingJwt = keyBindingJwt,
            credential = holderCredentials(publicJwk).filterIsInstance<SdJwtCredential>().single(),
            audience = "verifier",
            nonce = "nonce",
            sdHash = ShaUtils.calculateSha256Base64Url(hashablePresentation),
            presentationStringHashable = hashablePresentation,
        )

        presentation.presentationVerification("verifier", "nonce", null, setOf(JwsAlgorithm.ES256))

        val wrongAlgorithm = assertFailsWith<IllegalArgumentException> {
            presentation.copy(credential = holderCredentials().last()).presentationVerification(
                "verifier",
                "nonce",
                null,
                setOf(JwsAlgorithm.ED25519),
            )
        }
        assertTrue(wrongAlgorithm.message?.contains("algorithm is not allowed") == true)

        val tamperedParts = keyBindingJwt.split('.').toMutableList().apply {
            this[2] = (if (this[2].first() == 'A') "B" else "A") + this[2].drop(1)
        }
        assertFails {
            presentation.copy(keyBindingJwt = tamperedParts.joinToString(".")).presentationVerification(
                "verifier",
                "nonce",
                null,
            )
        }
    }

    @Test
    fun `holder cnf kid resolves did jwk as verification-only key`() = runTest {
        DidService.minimalInit()
        val did = "did:jwk:eyJjcnYiOiJQLTI1NiIsImt0eSI6IkVDIiwieCI6ImFjYklRaXVNczNpOF91c3pFakoydHBUdFJNNEVVM3l6OTFQSDZDZEgyVjAiLCJ5IjoiX0tjeUxqOXZXTXB0bm1LdG00NkdxRHo4d2Y3NEk1TEtncmwyR3pIM25TRSJ9"

        assertHolderKidResolves("$did#0")
    }

    @Test
    fun `holder cnf kid resolves JWK JCS did key as verification-only key`() = runTest {
        DidService.minimalInit()
        val publicJwk = """{"crv":"P-256","kty":"EC","x":"acbIQiuMs3i8_uszEjJ2tpTtRM4EU3yz91PH6CdH2V0","y":"_KcyLj9vWMptnmKtm46GqDz8wf74I5LKgrl2GzH3nSE"}"""
        val identifier = MultiBaseUtils.convertRawKeyToMultiBase58Btc(
            JsonCanonicalization.getCanonicalBytes(publicJwk),
            MultiCodecUtils.JwkJcsPubMultiCodecKeyCode,
        )
        val did = "did:key:$identifier"

        assertNotNull(DidService.resolve(did).getOrThrow()["verificationMethod"])
        assertHolderKidResolves("$did#${did.removePrefix("did:key:")}")
    }

    private suspend fun assertHolderKidResolves(keyId: String) {
        holderCredentials(keyId = keyId).forEach { credential ->
            val holderKey = assertNotNull(credential.getHolderCrypto2Key())
            assertNotNull(holderKey.capabilities.verifier)
            assertNull(holderKey.capabilities.signer)
            assertNull(holderKey.capabilities.privateKeyExporter)
        }
    }

    private suspend fun signingKey(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private fun holderCredentials(jwk: JsonObject? = null, keyId: String? = null): List<DigitalCredential> {
        val data = buildJsonObject {
            put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"))))
            if (jwk != null || keyId != null) {
                putJsonObject("cnf") {
                    jwk?.let { put("jwk", it) }
                    keyId?.let { put("kid", it) }
                }
            }
        }
        return listOf(
            W3C11(credentialData = data, signature = null, signed = null),
            SdJwtCredential(
                dmtype = CredentialDetectorTypes.SDJWTVCSubType.sdjwtvc,
                credentialData = data,
                signature = null,
                signed = null,
            ),
        )
    }
}
