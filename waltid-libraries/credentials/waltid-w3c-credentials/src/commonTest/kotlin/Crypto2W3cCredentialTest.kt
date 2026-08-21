import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.local.jwk.Crypto2DidJwkRegistrar
import id.walt.sdjwt.SDMap
import id.walt.w3c.PresentationBuilder
import id.walt.w3c.issuance.Issuer
import id.walt.w3c.issuance.Issuer.baseIssue
import id.walt.w3c.issuance.Issuer.mergingJwtIssue
import id.walt.w3c.issuance.Issuer.mergingSdJwtIssue
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.w3c.vc.vcs.W3CVC
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Crypto2W3cCredentialTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

    @Test
    fun `W3C credential signs and verifies directly and through inline JWK resolver`() = runTest {
        val key = key()
        val publicJwk = key.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk
        val publicJwkJson = Json.parseToJsonElement(publicJwk.data.toByteArray().decodeToString()) as JsonObject
        val credential = W3CVC.build(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential", "ExampleCredential"),
            "credentialSubject" to buildJsonObject {
                put("id", "did:example:holder")
                put("name", "Jane")
            },
        )
        val signed = credential.signJws(
            issuerKey = key,
            algorithm = JwsAlgorithm.ES256,
            issuerId = "https://issuer.example",
            issuerKid = "issuer-key",
            subjectDid = "did:example:holder",
            additionalJwtHeader = mapOf("jwk" to publicJwkJson),
        )
        val scheme = JwsSignatureScheme()

        assertTrue(scheme.verifyCrypto2(signed, key, setOf(JwsAlgorithm.ES256)).isSuccess)
        val resolved = scheme.verifyCrypto2(
            signed,
            setOf(JwsAlgorithm.ES256),
            Crypto2JwtKeyResolver(allowInlineJwk = true),
        )
        assertTrue(resolved.isSuccess)
        val payload = resolved.getOrThrow().jsonObject
        assertEquals("https://issuer.example", payload["iss"]?.toString()?.trim('"'))

        val parts = signed.split('.').toMutableList().apply {
            this[2] = (if (this[2].first() == 'A') "B" else "A") + this[2].drop(1)
        }
        assertFalse(scheme.verifyCrypto2(parts.joinToString("."), key, setOf(JwsAlgorithm.ES256)).isSuccess)
    }

    @Test
    fun `presentation builder signs with explicit crypto2 algorithm`() = runTest {
        val key = key()
        val builder = PresentationBuilder().apply {
            holderPubKeyJwk = buildJsonObject { put("kid", "holder-key") }
            audience = "verifier"
            nonce = "nonce"
            addCredential(JsonPrimitive("credential"))
        }
        val signed = builder.buildAndSign(key, JwsAlgorithm.ES256)
        val verified = CompactJws.verify(signed, key, JwsAlgorithm.ES256)

        assertEquals("JWT", verified.protectedHeader["typ"]?.toString()?.trim('"'))
    }

    @Test
    fun `W3C selective disclosure credential signs with crypto2`() = runTest {
        val key = key()
        val signed = W3CVC.build(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential", "ExampleCredential"),
            "credentialSubject" to buildJsonObject { put("name", "Jane") },
        ).signSdJwt(
            issuerKey = key,
            algorithm = JwsAlgorithm.ES256,
            issuerId = "https://issuer.example",
            subjectDid = "did:example:holder",
            disclosureMap = SDMap(emptyMap()),
        )

        val verified = CompactJws.verify(signed.substringBefore('~'), key, JwsAlgorithm.ES256)
        assertEquals("vc+sd-jwt", verified.protectedHeader["typ"]?.toString()?.trim('"'))
    }

    @Test
    fun `issuer facade issues JWT and SD-JWT credentials with crypto2`() = runTest {
        val key = key()
        val credential = W3CVC.build(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential", "ExampleCredential"),
            "credentialSubject" to buildJsonObject { put("name", "Jane") },
        )
        val baseIssued = credential.baseIssue(
            key = key,
            algorithm = JwsAlgorithm.ES256,
            issuerId = "https://issuer.example",
            subject = "did:example:holder",
            dataOverwrites = emptyMap(),
            dataUpdates = emptyMap(),
            additionalJwtHeaders = emptyMap(),
            additionalJwtOptions = emptyMap(),
        )
        val mergedJwt = credential.mergingJwtIssue(
            issuerKey = key,
            algorithm = JwsAlgorithm.ES256,
            issuerId = "https://issuer.example",
            subjectDid = "did:example:holder",
            mappings = JsonObject(emptyMap()),
            additionalJwtHeader = emptyMap(),
            additionalJwtOptions = emptyMap(),
        )
        val mergedSdJwt = credential.mergingSdJwtIssue(
            issuerKey = key,
            algorithm = JwsAlgorithm.ES256,
            issuerId = "https://issuer.example",
            subjectDid = "did:example:holder",
            mappings = JsonObject(emptyMap()),
            type = "vc+sd-jwt",
            additionalJwtHeaders = emptyMap(),
            additionalJwtOptions = emptyMap(),
            disclosureMap = SDMap(emptyMap()),
        )

        assertTrue(CompactJws.verify(baseIssued, key, JwsAlgorithm.ES256).payload.isNotEmpty())
        assertTrue(CompactJws.verify(mergedJwt, key, JwsAlgorithm.ES256).payload.isNotEmpty())
        assertTrue(
            CompactJws.verify(mergedSdJwt.substringBefore('~'), key, JwsAlgorithm.ES256).payload.isNotEmpty()
        )
        assertEquals(key.id.value, Issuer.getKidHeader(key))
    }

    @Test
    fun `issuer DID key IDs are qualified exactly once`() = runTest {
        val issuerDid = "did:web:issuer.example"
        val fullKeyId = "$issuerDid#signing-key"
        val didKey = "did:key:z6MkhYjV5FQHjGQm1JwPz"
        val didKeyVerificationMethod = "$didKey#${didKey.removePrefix("did:key:")}"
        val webKey = key("signing-key")
        val webThumbprint = publicJwkThumbprint(webKey)

        assertEquals("$issuerDid#$webThumbprint", Issuer.getKidHeader(webKey, issuerDid))
        assertEquals(fullKeyId, Issuer.getKidHeader(key(fullKeyId), issuerDid))
        assertEquals(
            didKeyVerificationMethod,
            Issuer.getKidHeader(key("ignored-for-self-identifying-did"), didKey),
        )
        assertEquals(didKeyVerificationMethod, Issuer.getKidHeader(key(didKeyVerificationMethod), didKey))
    }

    @Test
    fun `did jwk issuer kid uses the method verification fragment`() = runTest {
        val kmsStyleKey = key("org.tenant.kms.key_issuer")
        val didJwk = Crypto2DidJwkRegistrar().createByKey(kmsStyleKey, DidJwkCreateOptions()).did

        assertEquals("$didJwk#0", Issuer.getKidHeader(kmsStyleKey, didJwk))
        assertEquals("$didJwk#0", Issuer.getKidHeader(key("$didJwk#0"), didJwk))
        assertEquals("$didJwk#0", Issuer.getKidHeader(key("$didJwk#org.tenant.kms.key_issuer"), didJwk))
        assertEquals("$didJwk#0", Issuer.getKidHeader(key("#0"), didJwk))
    }

    @Test
    fun `did jwk credential signature resolves for method kid and non-method kid`() = runTest {
        DidService.minimalInit()
        val key = key("org.tenant.kms.key_issuer")
        val didJwk = Crypto2DidJwkRegistrar().createByKey(key, DidJwkCreateOptions()).did
        val payload = buildJsonObject {
            put("iss", didJwk)
            put("vct", "urn:example:pid")
        }.toString().encodeToByteArray()

        val methodKid = Issuer.getKidHeader(key, didJwk)
        assertEquals("$didJwk#0", methodKid)
        val scheme = JwsSignatureScheme()
        val issued = CompactJws.sign(
            payload = payload,
            key = key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("kid", methodKid)
                put("typ", "dc+sd-jwt")
            },
        )
        assertTrue(scheme.verifyCrypto2(issued, setOf(JwsAlgorithm.ES256)).isSuccess)

        val nonMethodKid = CompactJws.sign(
            payload = payload,
            key = key,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("kid", "$didJwk#org.tenant.kms.key_issuer")
                put("typ", "dc+sd-jwt")
            },
        )
        assertTrue(scheme.verifyCrypto2(nonMethodKid, setOf(JwsAlgorithm.ES256)).isSuccess)
    }

    private suspend fun publicJwkThumbprint(key: Key) =
        Jwk.sha256Thumbprint(key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec))

    private suspend fun key(id: String = "w3c-key") = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )
}
