package id.walt.crypto2.kms.csc

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.toSpkiDer
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.time.Instant

internal val NOW = Instant.parse("2026-11-24T10:00:00Z")
internal val NOT_BEFORE = Instant.parse("2026-01-01T00:00:00Z")
internal val NOT_AFTER = Instant.parse("2027-01-01T00:00:00Z")

internal const val CREDENTIAL_ID = "qtsp-seal-credential"
internal const val CLIENT_ID = "stub-client"
internal const val CLIENT_SECRET = "stub-client-secret"
internal const val PIN = "4711"
internal const val ACCESS_TOKEN = "stub-access-token"
internal const val SAD = "stub-signature-activation-data"

internal const val INFO_PATH = "/csc/v2/info"
internal const val TOKEN_PATH = "/oauth2/token"
internal const val CREDENTIAL_INFO_PATH = "/csc/v2/credentials/info"
internal const val AUTHORIZE_PATH = "/csc/v2/credentials/authorize"
internal const val SIGN_HASH_PATH = "/csc/v2/signatures/signHash"

/** A CSC v2.2 RSSP stub. Each property describes one part of its responses; tests change them before a call. */
internal class CscStub(var certificates: List<ByteArray>) {
    var specs = "2.2.0.0"
    var authTypes = listOf("oauth2client")
    var oauth2: String? = "https://rssp.example"
    var keyStatus = "enabled"
    var keyAlgorithms = listOf(CscClient.ECDSA_SHA256_OID)
    var keyCurve = CscClient.P256_OID
    var certificateStatus: String? = "valid"
    var authMode = "explicit"
    var authObjects = """[{"type":"Password","id":"PIN","format":"N","label":"PIN"}]"""
    var credentialInfoStatus = HttpStatusCode.OK
    var authorizeStatus = HttpStatusCode.OK
    var signHashStatus = HttpStatusCode.OK
    var signatureFor: suspend (hash: ByteArray) -> ByteArray = { error("The test configured no signature") }

    val requests = mutableListOf<String>()
    var tokenHost: String? = null
        private set
    private val bodies = mutableMapOf<String, String>()

    val client = HttpClient(MockEngine) { engine { addHandler { request -> respondTo(request) } } }

    fun body(path: String): String = requireNotNull(bodies[path]) { "No request to $path" }

    fun json(path: String): JsonObject = Json.parseToJsonElement(body(path)).jsonObject

    fun count(path: String): Int = requests.count { it == path }

    private suspend fun MockRequestHandleScope.respondTo(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        requests += path
        bodies[path] = request.bodyText()
        if (path != INFO_PATH && path != TOKEN_PATH) {
            assertEquals("Bearer $ACCESS_TOKEN", request.headers[HttpHeaders.Authorization])
        }
        return when (path) {
            INFO_PATH -> respondJson(
                """{"specs":"$specs","name":"Stub RSSP","logo":"https://rssp.example/logo.png","region":"AT",
                   |"lang":"en-US","description":"Stub","authType":${authTypes.toJsonArray()},
                   |${oauth2?.let { "\"oauth2\":\"$it\"," }.orEmpty()}"methods":["credentials/info"]}""".trimMargin()
            )
            TOKEN_PATH -> {
                tokenHost = request.url.host
                respondJson("""{"access_token":"$ACCESS_TOKEN","token_type":"Bearer","expires_in":3600}""")
            }
            CREDENTIAL_INFO_PATH -> if (credentialInfoStatus != HttpStatusCode.OK) {
                respondJson("""{"error":"invalid_token","error_description":"Expired token"}""", credentialInfoStatus)
            } else respondJson(
                """{"key":{"status":"$keyStatus","algo":${keyAlgorithms.toJsonArray()},"len":256,"curve":"$keyCurve"},
                   |"cert":{${certificateStatus?.let { "\"status\":\"$it\"," }.orEmpty()}
                   |"certificates":${certificates.map(Base64.Default::encode).toJsonArray()}},
                   |"auth":{"mode":"$authMode","objects":$authObjects},"SCAL":"2","multisign":1}""".trimMargin()
            )
            AUTHORIZE_PATH -> respondJson("""{"SAD":"$SAD","expiresIn":300}""", authorizeStatus)
            SIGN_HASH_PATH -> {
                if (signHashStatus != HttpStatusCode.OK) {
                    respondJson("""{"error":"invalid_request","error_description":"Invalid SAD $SAD"}""", signHashStatus)
                } else {
                    val hash = json(path)["hashes"]!!.jsonArray.single().jsonPrimitive.content
                    val signature = signatureFor(Base64.Default.decode(hash))
                    respondJson("""{"signatures":["${Base64.Default.encode(signature)}"]}""")
                }
            }
            else -> error("Unexpected CSC request: $path")
        }
    }

    private fun MockRequestHandleScope.respondJson(content: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(content, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun List<String>.toJsonArray() = joinToString(",", "[", "]") { "\"$it\"" }
}

internal fun HttpRequestData.bodyText(): String =
    (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
        ?: error("Expected a byte-array request body")

/** The RSSP's private key, as a software key. */
internal class TestRsspKey private constructor(val key: SoftwareKey, val spki: ByteArray) {
    suspend fun sign(message: ByteArray, encoding: EcdsaSignatureEncoding): ByteArray =
        requireNotNull(key.capabilities.signer).sign(message, es256(encoding))

    suspend fun verify(message: ByteArray, signature: ByteArray, encoding: EcdsaSignatureEncoding): Boolean =
        requireNotNull(key.capabilities.verifier).verify(message, signature, es256(encoding))

    companion object {
        suspend fun generate(curve: EcCurve = EcCurve.P256): TestRsspKey {
            val spec = KeySpec.Ec(curve)
            val key = CryptographySoftwareKeyProvider().generate(
                GenerateSoftwareKeyRequest(KeyId("rssp-key"), spec, setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
            )
            val spki = requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey().toSpkiDer(spec)
            return TestRsspKey(key, spki.data.toByteArray())
        }
    }
}

internal fun es256(encoding: EcdsaSignatureEncoding) = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, encoding)

/** A self-signed X.509 certificate for [subject], built with waltid-x509. */
internal suspend fun testCertificate(
    subject: Key,
    notBefore: Instant = NOT_BEFORE,
    notAfter: Instant = NOT_AFTER,
    signatureAlgorithm: SignatureAlgorithm = es256(EcdsaSignatureEncoding.DER),
): ByteArray = X509CertificateUtil.createSelfSignedCertificate(subject, signatureAlgorithm) {
    subjectDn = "CN=Test qualified seal"
    validity = X509Certificate.Validity(notBefore, notAfter)
}.encodedDer.toByteArray()

/** A leaf certificate for [subject] issued by a CA certificate of [ca], followed by that CA certificate. */
internal suspend fun testChain(subject: Key, ca: Key): List<ByteArray> {
    val caCertificate = X509CertificateUtil.createSelfSignedCertificate(ca, es256(EcdsaSignatureEncoding.DER)) {
        subjectDn = "CN=Test QTSP CA"
        validity = X509Certificate.Validity(NOT_BEFORE, NOT_AFTER)
    }
    val leaf = X509CertificateUtil.createCertificate(ca, caCertificate, es256(EcdsaSignatureEncoding.DER)) {
        subjectDn = "CN=Test qualified seal"
        validity = X509Certificate.Validity(NOT_BEFORE, NOT_AFTER)
        subjectPublicKey(subject)
    }
    return listOf(leaf.encodedDer.toByteArray(), caCertificate.encodedDer.toByteArray())
}

/**
 * [certificate] with the point format byte of its P-256 subject public key [spki] changed from 0x04 (uncompressed) to
 * 0x05, which is not a valid EC point encoding.
 */
internal fun withUndecodablePoint(certificate: ByteArray, spki: ByteArray): ByteArray {
    val start = (0..certificate.size - spki.size).single { offset ->
        spki.indices.all { certificate[offset + it] == spki[it] }
    }
    val format = start + spki.size - P256_UNCOMPRESSED_POINT_BYTES
    check(certificate[format] == 0x04.toByte()) { "Expected an uncompressed P-256 point" }
    return certificate.copyOf().apply { this[format] = 0x05 }
}

private const val P256_UNCOMPRESSED_POINT_BYTES = 65
