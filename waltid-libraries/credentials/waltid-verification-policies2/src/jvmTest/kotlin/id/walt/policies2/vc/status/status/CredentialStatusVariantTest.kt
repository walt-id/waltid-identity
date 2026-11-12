@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.policies2.vc.status.status

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.MdocsCredential
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.Status
import id.walt.mdoc.objects.mso.UniformResourceIdentifier
import id.walt.policies2.vc.policies.StatusPolicy
import id.walt.policies2.vc.policies.status.entry.IETFEntryExtractor
import id.walt.policies2.vc.policies.status.entry.MDocEntryExtractor
import id.walt.policies2.vc.policies.status.model.IETFEntry
import id.walt.policies2.vc.policies.status.model.IETFStatusPolicyAttribute
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialStatusVariantTest {
    private val policy = StatusPolicy(IETFStatusPolicyAttribute(value = 0u))
    private val ietfExtractor = IETFEntryExtractor(MDocEntryExtractor())
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun issueKeys() {
        MdocIssuerFixture.ensureKeys()
    }

    @Test
    fun `sd-jwt without a status claim reports that nothing can be evaluated`() = runTest {
        assertStatusUnavailable(sdJwt(statusJson = null))
    }

    @Test
    fun `sd-jwt with an explicit null status claim fails because null is not a status object`() = runTest {
        val result = policy.verify(CredentialParser.parseOnly(sdJwt(statusJson = "null")))
        assertEquals(
            "SD-JWT VC status claim must be a JSON object with status_list, but was null.",
            result.exceptionOrNull()?.message,
        )
    }

    @Test
    fun `sd-jwt status claim shapes that are not an IETF status list fail with the shape named`() = runTest {
        val cases = listOf(
            "revoked-string" to "\"revoked\"" to
                "SD-JWT VC status claim must be a JSON object with status_list, but was a string.",
            "number" to "0" to
                "SD-JWT VC status claim must be a JSON object with status_list, but was a number.",
            "array" to "[]" to
                "SD-JWT VC status claim must be a JSON object with status_list, but was an array.",
            "empty-object" to "{}" to
                "SD-JWT VC status claim has no status_list. $IETF_STATUS_LIST. Found members: (none).",
            "unrelated-member" to """{"purpose":"revocation"}""" to
                "SD-JWT VC status claim has no status_list. $IETF_STATUS_LIST. Found members: purpose.",
            "status-list-string" to """{"status_list":"https://status.example/list"}""" to
                "SD-JWT VC status.status_list must be a JSON object with idx and uri, but was a string.",
            "status-list-missing-both" to """{"status_list":{}}""" to
                "SD-JWT VC status.status_list is missing idx and uri.",
            "status-list-missing-uri" to """{"status_list":{"idx":0}}""" to
                "SD-JWT VC status.status_list is missing uri.",
            "status-list-missing-idx" to """{"status_list":{"uri":"https://status.example/list"}}""" to
                "SD-JWT VC status.status_list is missing idx.",
            "idx-string" to """{"status_list":{"idx":"0","uri":"https://status.example/list"}}""" to
                "SD-JWT VC status.status_list.idx must be an unsigned integer, but was a string.",
            "idx-negative" to """{"status_list":{"idx":-1,"uri":"https://status.example/list"}}""" to
                "SD-JWT VC status.status_list.idx must be an unsigned integer, but was a number.",
            "idx-boolean" to """{"status_list":{"idx":true,"uri":"https://status.example/list"}}""" to
                "SD-JWT VC status.status_list.idx must be an unsigned integer, but was a boolean.",
            "uri-number" to """{"status_list":{"idx":0,"uri":1}}""" to
                "SD-JWT VC status.status_list.uri must be a non-blank URI string, but was a number.",
            "uri-blank" to """{"status_list":{"idx":0,"uri":"  "}}""" to
                "SD-JWT VC status.status_list.uri must be a non-blank URI string, but was blank.",
        )
        cases.forEach { (nameAndJson, expected) ->
            val (name, statusJson) = nameAndJson
            val result = policy.verify(CredentialParser.parseOnly(sdJwt(statusJson)))
            assertEquals(expected, result.exceptionOrNull()?.message, name)
        }
    }

    @Test
    fun `sd-jwt with an IETF status list is extracted`() = runTest {
        val credential = CredentialParser.parseOnly(
            sdJwt("""{"status_list":{"idx":7,"uri":"https://status.example/list","extra":true}}""")
        )
        val extracted = ietfExtractor.extract(credential)
        val entry = json.decodeFromJsonElement<IETFEntry>(extracted!!)
        assertEquals(7uL, entry.index)
        assertEquals("https://status.example/list", entry.uri)
    }

    @Test
    fun `mdoc without a status object reports that nothing can be evaluated`() = runTest {
        val result = policy.verify(MdocIssuerFixture.issue(status = null))
        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        assertEquals(JsonPrimitive(false), result.getOrThrow().jsonObject["policy_available"])
    }

    @Test
    fun `mdoc status list is extracted even when another status mechanism is also present`() = runTest {
        val uri = "https://status.example/list"
        val credential = MdocIssuerFixture.issue(
            Status(
                statusList = statusList(index = 42u, uri = uri),
                identifierList = identifierList(),
            )
        )
        val entry = json.decodeFromJsonElement<IETFEntry>(MDocEntryExtractor().extract(credential)!!)
        assertEquals(42uL, entry.index)
        assertEquals(uri, entry.uri)
    }

    @Test
    fun `mdoc status objects without a status list fail with the mechanism named`() = runTest {
        val cases = listOf(
            "identifier-list" to Status(identifierList = identifierList()) to
                "mdoc has ISO/IEC 18013-5 identifier_list and no status_list. $IETF_STATUS_LIST.",
            "etsi-flat" to Status(
                type = "TokenStatusList",
                purpose = "revocation",
                index = 4,
                uri = UniformResourceIdentifier("https://status.example/etsi"),
            ) to
                "mdoc has ETSI TS 119 472-1 flat status (type, purpose, index, uri) and no status_list. $IETF_STATUS_LIST.",
            "etsi-type-only" to Status(type = "TokenStatusList") to
                "mdoc has ETSI TS 119 472-1 flat status (type) and no status_list. $IETF_STATUS_LIST.",
            "identifier-list-and-etsi" to Status(
                identifierList = identifierList(),
                type = "TokenStatusList",
                uri = UniformResourceIdentifier("https://status.example/etsi"),
            ) to
                "mdoc has ISO/IEC 18013-5 identifier_list and ETSI TS 119 472-1 flat status (type, uri) and no status_list. $IETF_STATUS_LIST.",
        )
        cases.forEach { (nameAndStatus, expected) ->
            val (name, status) = nameAndStatus
            val result = policy.verify(MdocIssuerFixture.issue(status))
            assertEquals(expected, result.exceptionOrNull()?.message, name)
        }
    }

    @Test
    fun `an issued empty status object does not survive encoding and is treated as no status`() = runTest {
        val credential = MdocIssuerFixture.issue(Status())
        assertNull(credential.documentMso.status)
        val result = policy.verify(credential)
        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        assertEquals(JsonPrimitive(false), result.getOrThrow().jsonObject["policy_available"])
    }

    @Test
    fun `mdoc extractor returns null for an sd-jwt`() = runTest {
        val credential = CredentialParser.parseOnly(sdJwt(statusJson = null))
        assertNull(MDocEntryExtractor().extract(credential))
    }

    private suspend fun assertStatusUnavailable(rawCredential: String) {
        val result = policy.verify(CredentialParser.parseOnly(rawCredential))
        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        assertEquals(JsonPrimitive(false), result.getOrThrow().jsonObject["policy_available"])
    }

    private fun sdJwt(statusJson: String?): String = buildString {
        append(
            """
            {"vct":"urn:eudi:pid:1","iss":"https://issuer.example.com","iat":1720000000,"exp":2000000000,"_sd_alg":"sha-256"
            """.trimIndent()
        )
        if (statusJson != null) append(""","status":$statusJson""")
        append("}")
    }

    private fun statusList(index: ULong, uri: String) =
        Status.StatusListInfo(index = index, uri = UniformResourceIdentifier(uri))

    private fun identifierList() = Status.IdentifierListInfo(
        id = byteArrayOf(0x01, 0x02),
        uri = UniformResourceIdentifier("https://status.example/identifier-list"),
    )

    private companion object {
        const val IETF_STATUS_LIST =
            "credential-status evaluates an IETF Token Status List (status.status_list.idx and status.status_list.uri)"
    }
}

private object MdocIssuerFixture {
    private const val DOC_TYPE = "org.iso.18013.5.1.mDL"
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private lateinit var issuerKey: Key
    private lateinit var holderCoseKey: id.walt.cose.CoseKey
    private lateinit var certificateDer: ByteArray

    fun ensureKeys() {
        if (::issuerKey.isInitialized) return
        runBlocking {
            issuerKey = runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("status-issuer"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )
            )
            val holderKey = runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("status-holder"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.KEY_AGREEMENT),
                )
            )
            holderCoseKey = (holderKey.capabilities.publicKeyExporter!!.exportPublicKey() as EncodedKey.Jwk).toCoseKey()
            certificateDer = X509CertificateUtil.createSelfSignedCertificate(
                issuerKey,
                SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
            ) { subjectDn = "CN=Status issuer" }.encodedDer.toByteArray()
        }
    }

    suspend fun issue(status: Status?): MdocsCredential {
        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            signatureAlgorithm = Cose.Algorithm.ES256,
            issuerCertificate = listOf(CoseCertificate(certificateDer)),
            holderKey = holderCoseKey,
            docType = DOC_TYPE,
            data = MdocIssuer.MdocUniversalIssuanceData(
                namespaces = mapOf("org.iso.18013.5.1" to buildJsonObject { put("family_name", "Doe") })
            ),
            status = status,
        )
        val document = Document(docType = DOC_TYPE, issuerSigned = issuerSigned)
        return MdocsCredential(
            credentialData = buildJsonObject {
                put("docType", DOC_TYPE)
                put("org.iso.18013.5.1", buildJsonObject { put("family_name", "Doe") })
            },
            signed = base64.encode(coseCompliantCbor.encodeToByteArray(document)),
            docType = DOC_TYPE,
        )
    }
}
