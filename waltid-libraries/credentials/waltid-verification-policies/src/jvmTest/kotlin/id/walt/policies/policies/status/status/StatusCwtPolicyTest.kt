package id.walt.policies.policies.status.status

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.toCoseSigner
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.policies.policies.StatusPolicy
import id.walt.policies.policies.status.signature.RAW_CREDENTIAL_CONTEXT_KEY
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatusCwtPolicyTest {
    private val sut = StatusPolicy()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private lateinit var baseUrl: String
    private lateinit var did: String
    private lateinit var issuerCertificateDer: ByteArray
    private val tokens = mutableMapOf<String, Pair<ByteArray, ContentType>>()

    @BeforeAll
    fun startServer() {
        server = embeddedServer(Netty, port = 0) {
            routing {
                get("credentials/{id}") {
                    val id = call.parameters["id"] ?: error("missing id")
                    val (body, contentType) = tokens[id] ?: run {
                        call.response.status(HttpStatusCode.NotFound)
                        return@get
                    }
                    if (contentType.match(ContentType.Text.Plain)) {
                        call.respondText(body.toHexString(), contentType)
                    } else {
                        call.respondBytes(body, contentType)
                    }
                }
            }
        }.start(wait = false)
        val port = runBlocking { server.engine.resolvedConnectors().first().port }
        baseUrl = "http://localhost:$port"

        runBlocking {
        DidService.minimalInit()
        val didKey = JWKKey.generate(KeyType.Ed25519)
        did = DidService.registerByKey("key", didKey).did
        val didKeyId = DidService.resolveToCrypto2Keys(did).getOrThrow().single().id.value
        val didHeaders = CoseHeaders(algorithm = Cose.Algorithm.EdDSA, kid = didKeyId.encodeToByteArray())
        val didSigner = didKey.toCoseSigner()

        val certificateKey = JWKKey.generate(KeyType.secp256r1)
        val certificate = X509CertificateUtil.createSelfSignedCertificate(certificateKey) {
            subjectDn = "CN=mdoc status issuer"
            subjectPublicKeySelfSigned()
        }
        issuerCertificateDer = certificate.encodedDer.toByteArray()
        val certificateHeaders = IetfStatusListTokens.certificateHeaders(Cose.Algorithm.ES256, issuerCertificateDer)
        val certificateSigner = certificateKey.toCoseSigner()

        suspend fun publish(
            id: String,
            status: UInt,
            bits: Int = 1,
            contentType: ContentType = ContentType("application", "statuslist+cwt"),
            certificateSigned: Boolean = false,
        ) {
            val body = IetfStatusListTokens.signedCwt(
                uri = "$baseUrl/credentials/$id",
                signer = if (certificateSigned) certificateSigner else didSigner,
                protectedHeaders = if (certificateSigned) certificateHeaders else didHeaders,
                index = 0,
                status = status,
                bits = bits,
            )
            tokens[id] = body to contentType
        }

        publish("cwt-valid", 0u)
        publish("cwt-revoked", 1u)
        publish("cwt-suspended", 2u, bits = 2)
        publish("cwt-valid-hex", 0u, contentType = ContentType.Text.Plain)
        publish("cwt-valid-octet", 0u, contentType = ContentType.Application.OctetStream)
        publish("cwt-valid-mdoc", 0u, certificateSigned = true)
        publish("cwt-revoked-mdoc", 1u, certificateSigned = true)
        tokens["cwt-unsigned"] = IetfStatusListTokens.unsignedCwt(index = 0, status = 0u) to
            ContentType("application", "statuslist+cwt")
        }
    }

    @AfterAll
    fun stopServer() {
        if (::server.isInitialized) {
            server.stop()
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun verifyCwtStatusList(
        name: String,
        id: String,
        shape: CredentialShape,
        expectedValue: Int,
        expectSuccess: Boolean,
    ) = runTest {
        val credential = credential(id, shape)
        val args = json.parseToJsonElement(
            """{"discriminator":"ietf","value":$expectedValue}"""
        )
        val context = if (shape == CredentialShape.RAW_CREDENTIAL) {
            mapOf(RAW_CREDENTIAL_CONTEXT_KEY to """{"vct":"https://credentials.example.com/identity","iss":"$did"}""")
        } else {
            emptyMap()
        }
        val result = sut.verify(credential, args, context)
        assertEquals(expectSuccess, result.isSuccess, "$name: ${result.exceptionOrNull()?.message}")
        if (!expectSuccess) {
            assertTrue(
                result.exceptionOrNull()!!.message.orEmpty().contains("Status validation failed"),
                result.exceptionOrNull()?.message,
            )
        }
    }

    @Test
    fun `unsigned CWT is rejected`() = runTest {
        val credential = jsonStatus("cwt-unsigned", includeIssuer = true)
        val args = json.parseToJsonElement("""{"discriminator":"ietf","value":0}""")
        val result = sut.verify(credential, args, emptyMap())
        assertTrue(result.isFailure, result.exceptionOrNull()?.message)
        assertTrue(
            result.exceptionOrNull()!!.message.orEmpty().contains("signature verification failed"),
            result.exceptionOrNull()?.message,
        )
    }

    companion object {
        @JvmStatic
        fun cases(): Stream<Arguments> = Stream.of(
            arguments("binary CWT valid (value 0)", "cwt-valid", CredentialShape.ISSUER, 0, true),
            arguments("binary CWT revoked fails expected 0", "cwt-revoked", CredentialShape.ISSUER, 0, false),
            arguments("binary CWT revoked succeeds expected 1", "cwt-revoked", CredentialShape.ISSUER, 1, true),
            arguments("binary CWT suspended succeeds expected 2", "cwt-suspended", CredentialShape.ISSUER, 2, true),
            arguments("binary CWT suspended fails expected 0", "cwt-suspended", CredentialShape.ISSUER, 0, false),
            arguments("hex-encoded CWT over text/plain", "cwt-valid-hex", CredentialShape.ISSUER, 0, true),
            arguments("binary CWT over octet-stream via COSE tag", "cwt-valid-octet", CredentialShape.ISSUER, 0, true),
            arguments("nested issuer object", "cwt-valid", CredentialShape.NESTED_ISSUER, 0, true),
            arguments("vc.issuer", "cwt-valid", CredentialShape.VC_ISSUER, 0, true),
            arguments("raw credential in context", "cwt-valid", CredentialShape.RAW_CREDENTIAL, 0, true),
            arguments("mdoc status reference against binary CWT", "cwt-valid-mdoc", CredentialShape.MDOC, 0, true),
            arguments("mdoc status reference revoked CWT fails expected 0", "cwt-revoked-mdoc", CredentialShape.MDOC, 0, false),
        )
    }

    private fun credential(id: String, shape: CredentialShape): JsonObject = when (shape) {
        CredentialShape.ISSUER -> jsonStatus(id, includeIssuer = true)
        CredentialShape.NESTED_ISSUER -> JsonObject(
            jsonStatus(id, includeIssuer = false) + buildJsonObject {
                putJsonObject("issuer") { put("id", did) }
            }
        )
        CredentialShape.VC_ISSUER -> JsonObject(
            jsonStatus(id, includeIssuer = false) + buildJsonObject {
                putJsonObject("vc") { put("issuer", did) }
            }
        )
        CredentialShape.RAW_CREDENTIAL -> jsonStatus(id, includeIssuer = false)
        CredentialShape.MDOC -> mdocStatus(id)
    }

    private fun jsonStatus(id: String, includeIssuer: Boolean): JsonObject = buildJsonObject {
        if (includeIssuer) put("iss", did)
        putJsonObject("status") {
            putJsonObject("status_list") {
                put("idx", 0)
                put("uri", "$baseUrl/credentials/$id")
            }
        }
    }

    private fun mdocStatus(id: String): JsonObject = buildJsonObject {
        put(
            "mdoc",
            IetfStatusListTokens.mdocWithStatus(
                index = 0u,
                uri = "$baseUrl/credentials/$id",
                issuerCertificateDer = issuerCertificateDer,
            ).toCBORHex(),
        )
    }

    enum class CredentialShape {
        ISSUER,
        NESTED_ISSUER,
        VC_ISSUER,
        RAW_CREDENTIAL,
        MDOC,
    }
}
