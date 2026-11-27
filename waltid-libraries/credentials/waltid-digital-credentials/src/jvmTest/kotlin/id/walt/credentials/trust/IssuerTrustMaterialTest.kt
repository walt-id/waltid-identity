package id.walt.credentials.trust

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@OptIn(ExperimentalSerializationApi::class)
class IssuerTrustMaterialTest {

    @Test
    fun `mdoc yields its x5chain, leaf first, once IssuerAuth verifies with the leaf key`() = runTest {
        val signer = generateKey("mdoc-signer")
        val leaf = selfSignedCertificate(signer, "CN=Document signer")
        val root = selfSignedCertificate(generateKey("root"), "CN=Root")

        val material = mdoc(signer, listOf(leaf, root)).verifiedIssuerTrustMaterial()

        val chain = assertIs<IssuerTrustMaterial.CertificateChain>(material).pemChain
        assertEquals(listOf(pem(leaf), pem(root)), chain)
    }

    @Test
    fun `mdoc with a forged IssuerAuth signature is rejected`() = runTest {
        val signer = generateKey("mdoc-signer")
        val credential = mdoc(signer, listOf(selfSignedCertificate(signer, "CN=Document signer")))
        val issuerAuth = credential.document.issuerSigned.issuerAuth
        val forged = issuerAuth.signature.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        val document = credential.document.copy(
            issuerSigned = IssuerSigned.fromIssuerSignedLists(
                namespaces = requireNotNull(credential.document.issuerSigned.namespaces),
                issuerAuth = issuerAuth.copy(signature = forged),
            )
        )

        val error = assertFailsWith<IssuerTrustMaterialException> {
            parse(base64Url.encode(coseCompliantCbor.encodeToByteArray(document))).verifiedIssuerTrustMaterial()
        }
        assertContains(error.message!!, "does not verify")
    }

    @Test
    fun `mdoc signed by a key its x5chain leaf does not hold is rejected`() = runTest {
        val certified = generateKey("certified")
        val credential = mdoc(generateKey("actual-signer"), listOf(selfSignedCertificate(certified, "CN=Listed signer")))

        val error = assertFailsWith<IssuerTrustMaterialException> { credential.verifiedIssuerTrustMaterial() }
        assertContains(error.message!!, "does not verify")
    }

    @Test
    fun `x5c-signed SD-JWT VC yields its x5c chain`() = runTest {
        val signer = generateKey("sd-jwt-signer")
        val certificate = selfSignedCertificate(signer, "CN=SD-JWT issuer")

        val material = parse("${signedJwt(signer, "dc+sd-jwt", certificate)}~").verifiedIssuerTrustMaterial()

        assertEquals(listOf(pem(certificate)), assertIs<IssuerTrustMaterial.CertificateChain>(material).pemChain)
    }

    @Test
    fun `x5c-signed W3C VC as JWT yields its x5c chain`() = runTest {
        val signer = generateKey("jwt-vc-signer")
        val certificate = selfSignedCertificate(signer, "CN=JWT VC issuer")

        val material = parse(signedJwt(signer, "JWT", certificate, w3c = true)).verifiedIssuerTrustMaterial()

        assertEquals(listOf(pem(certificate)), assertIs<IssuerTrustMaterial.CertificateChain>(material).pemChain)
    }

    @Test
    fun `SD-JWT VC with a forged signature is rejected`() = runTest {
        val signer = generateKey("sd-jwt-signer")
        val jwt = signedJwt(signer, "dc+sd-jwt", selfSignedCertificate(signer, "CN=SD-JWT issuer"))
        val (header, payload, signature) = jwt.split('.')
        val forged = base64Url.decode(signature).also { it[0] = (it[0].toInt() xor 1).toByte() }

        val error = assertFailsWith<IssuerTrustMaterialException> {
            parse("$header.$payload.${base64Url.encode(forged)}~").verifiedIssuerTrustMaterial()
        }
        assertContains(error.message!!, "does not verify")
    }

    @Test
    fun `SD-JWT VC signed by a key its x5c leaf does not hold is rejected`() = runTest {
        val certificate = selfSignedCertificate(generateKey("certified"), "CN=Listed issuer")
        val jwt = signedJwt(generateKey("actual-signer"), "dc+sd-jwt", certificate)

        assertFailsWith<IssuerTrustMaterialException> { parse("$jwt~").verifiedIssuerTrustMaterial() }
    }

    @Test
    fun `did-jwk-signed JWT without x5c yields the public key, not a chain`() = runTest {
        DidService.minimalInit()
        val signer = generateKey("did-signer")
        val did = "did:jwk:" + base64Url.encode(publicJwk(signer).toString().encodeToByteArray())
        val jwt = CompactJws.sign(
            payload = buildJsonObject {
                put("iss", did)
                put("vct", "https://example.org/vct")
            }.toString().encodeToByteArray(),
            key = signer,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", "dc+sd-jwt")
                put("kid", "$did#0")
            },
        )

        val material = parse("$jwt~").verifiedIssuerTrustMaterial()

        val jwk = Json.parseToJsonElement(assertIs<IssuerTrustMaterial.PublicKey>(material).jwk).jsonObject
        assertEquals(publicJwk(signer)["x"], jwk["x"])
    }

    @Test
    fun `JWT with only a self-asserted inline jwk header is rejected`() = runTest {
        val signer = generateKey("inline-signer")
        val jwt = CompactJws.sign(
            payload = buildJsonObject {
                put("iss", "urn:example:issuer")
                put("vct", "https://example.org/vct")
            }.toString().encodeToByteArray(),
            key = signer,
            algorithm = JwsAlgorithm.ES256,
            protectedHeader = buildJsonObject {
                put("typ", "dc+sd-jwt")
                put("jwk", publicJwk(signer))
            },
        )

        val error = assertFailsWith<IssuerTrustMaterialException> { parse("$jwt~").verifiedIssuerTrustMaterial() }
        assertContains(error.message!!, "could not be resolved")
    }

    // ---------------------------------------------------------------------------

    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private val certificateAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)

    private suspend fun generateKey(id: String, usages: Set<KeyUsage> = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)): Key =
        runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), usages))

    private suspend fun publicJwk(key: Key): JsonObject = Json.parseToJsonElement(
        key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec).data.toByteArray().decodeToString()
    ).jsonObject

    /** DER bytes of a self-signed certificate holding [key]. */
    private suspend fun selfSignedCertificate(key: Key, subject: String): ByteArray =
        X509CertificateUtil.createSelfSignedCertificate(key, certificateAlgorithm) { subjectDn = subject }
            .encodedDer.toByteArray()

    private fun pem(der: ByteArray): String = buildString {
        appendLine("-----BEGIN CERTIFICATE-----")
        Base64.encode(der).chunked(64).forEach { appendLine(it) }
        appendLine("-----END CERTIFICATE-----")
    }

    private suspend fun parse(raw: String): DigitalCredential = CredentialParser.detectAndParse(raw).second

    private suspend fun mdoc(signer: Key, x5chain: List<ByteArray>): MdocsCredential {
        val holderKey = generateKey("holder", setOf(KeyUsage.KEY_AGREEMENT))
        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = signer,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = x5chain.map { CoseCertificate(it) },
            holderKey = (holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey(),
            docType = "org.example.mdoc",
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf("org.example" to buildJsonObject { put("given_name", "Jane") })
            ),
        )
        val document = Document(docType = "org.example.mdoc", issuerSigned = issuerSigned)
        return assertIs<MdocsCredential>(parse(base64Url.encode(coseCompliantCbor.encodeToByteArray(document))))
    }

    private suspend fun signedJwt(signer: Key, typ: String, x5c: ByteArray, w3c: Boolean = false): String = CompactJws.sign(
        payload = buildJsonObject {
            put("iss", "https://issuer.example")
            if (w3c) {
                putJsonObject("vc") {
                    putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                    putJsonArray("type") { add("VerifiableCredential") }
                    putJsonObject("credentialSubject") { put("given_name", "Jane") }
                }
            } else {
                put("vct", "https://example.org/vct")
            }
        }.toString().encodeToByteArray(),
        key = signer,
        algorithm = JwsAlgorithm.ES256,
        protectedHeader = buildJsonObject {
            put("typ", typ)
            putJsonArray("x5c") { add(Base64.encode(x5c)) }
        },
    )
}
