package id.walt.policies.policies.status.status

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseSigner
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.did.dids.DidService
import id.walt.policies.policies.status.model.IETFStatusContent
import id.walt.policies.policies.status.model.IETFStatusPolicyAttribute
import id.walt.statuslist.codec.TokenStatusListCodec
import id.walt.statuslist.model.IndexedStatus
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class StatusCredentialTestServer {

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private lateinit var baseUrl: String

    lateinit var credentials: Map<String, List<TestStatusResource>>
        private set

    val port: Int
        get() = _port ?: throw IllegalStateException("Server not started yet")

    private var _port: Int? = null
    private var started = false

    suspend fun start() {
        if (!started) {
            server = embeddedServer(Netty, port = 0, module = { module() }).start(wait = false)

            _port = server.engine.resolvedConnectors().first().port
            baseUrl = "http://localhost:$port"

            credentials = resourceReader.readResourcesBySubfolder(
                "status",
                placeholderValue = "$baseUrl/$STATUS_CREDENTIAL_PATH"
            )
            DidService.minimalInit()
            val issuer = StatusListIssuer()
            credentials = signIetfStatusCredentials(credentials, issuer)
            val negatives = negativeIetfCredentials(credentials, issuer)
            credentials = credentials + ("tokenstatuslist" to credentials.getValue("tokenstatuslist") + negatives)

            started = true
        }
    }

    fun stop() {
        if (started) {
            server.stop()
            started = false
        }
    }

    companion object {
        private const val STATUS_CREDENTIAL_PATH = "credentials"
        private val resourceReader = StatusTestUtils.TestResourceReader()
        private val json = Json { ignoreUnknownKeys = true }
    }

    private fun Application.module() {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        routing {
            get("credentials/{id}") {
                val id = call.parameters.getOrFail("id")
                val statusCredential = getStatusCredentialContent(credentials.values.flatten(), id)
                requireNotNull(statusCredential)
                call.respond<String>(statusCredential)
            }
        }
    }

    private fun getStatusCredentialContent(resources: List<TestStatusResource>, targetId: String): String? =
        resources.find { it.id == targetId }?.let { testResource ->
            when (val data = testResource.data) {
                is MultiStatusResourceData -> data.statusCredential.firstOrNull()?.content
                is SingleStatusResourceData -> data.statusCredential
            }
        } ?: resources
            .mapNotNull { it.data as? MultiStatusResourceData }
            .flatMap { it.statusCredential }
            .find { it.id == targetId }
            ?.content

    private suspend fun signIetfStatusCredentials(
        resources: Map<String, List<TestStatusResource>>,
        issuer: StatusListIssuer,
    ): Map<String, List<TestStatusResource>> = resources.mapValues { (_, entries) ->
        entries.map { resource ->
            val data = resource.data
            if (data !is SingleStatusResourceData || !isIetfStatusJwt(data.statusCredential)) {
                resource
            } else {
                val uri = "$baseUrl/$STATUS_CREDENTIAL_PATH/${resource.id}"
                resource.copy(
                    data = data.copy(
                        statusCredential = issuer.signJwt(data.statusCredential, uri),
                        holderCredential = withIssuer(data.holderCredential, issuer.did),
                    )
                )
            }
        }
    }

    private suspend fun negativeIetfCredentials(
        resources: Map<String, List<TestStatusResource>>,
        issuer: StatusListIssuer,
    ): List<TestStatusResource> {
        val source = resources.values.flatten().first {
            it.data is SingleStatusResourceData && it.data.valid && isIetfStatusJwt(it.data.statusCredential)
        }
        val sourceData = source.data as SingleStatusResourceData
        val attacker = StatusListIssuer()
        return listOf(
            tampered(source, sourceData, issuer),
            unauthorized(source, sourceData, issuer, attacker),
            rejectedJwt(source, sourceData, issuer, id = "tokenstatuslist-wrong-subject", subject = "https://evil.example/list", exp = null, exception = "subject does not match"),
            rejectedJwt(source, sourceData, issuer, id = "tokenstatuslist-expired", subject = null, exp = 1_700_000_000, exception = "expired"),
            cwtResource(issuer, id = "tokenstatuslist-cwt-valid", subjectOverride = null, exp = 4_000_000_000, valid = true, exception = null),
            cwtResource(issuer, id = "tokenstatuslist-cwt-wrong-subject", subjectOverride = "https://evil.example/list", exp = 4_000_000_000, valid = false, exception = "subject does not match"),
            cwtResource(issuer, id = "tokenstatuslist-cwt-expired", subjectOverride = null, exp = 1_700_000_000, valid = false, exception = "expired"),
        )
    }

    private fun tampered(
        source: TestStatusResource,
        sourceData: SingleStatusResourceData,
        issuer: StatusListIssuer,
    ): TestStatusResource {
        val id = "tokenstatuslist-tampered-signature"
        val parts = sourceData.statusCredential.split('.')
        val signature = parts[2]
        val tamperedSignature = (if (signature.first() == 'A') 'B' else 'A') + signature.drop(1)
        return TestStatusResource(
            id = id,
            data = sourceData.copy(
                statusCredential = "${parts[0]}.${parts[1]}.$tamperedSignature",
                holderCredential = holder(issuer.did, id, source.id, sourceData.holderCredential),
                valid = false,
                exception = "signature verification failed",
            ),
        )
    }

    private suspend fun unauthorized(
        source: TestStatusResource,
        sourceData: SingleStatusResourceData,
        issuer: StatusListIssuer,
        attacker: StatusListIssuer,
    ): TestStatusResource {
        val id = "tokenstatuslist-unauthorized-signer"
        val uri = "$baseUrl/$STATUS_CREDENTIAL_PATH/$id"
        return TestStatusResource(
            id = id,
            data = sourceData.copy(
                statusCredential = attacker.signJwt(sourceData.statusCredential, uri),
                holderCredential = holder(issuer.did, id, source.id, sourceData.holderCredential),
                valid = false,
                exception = "does not match credential issuer",
            ),
        )
    }

    private suspend fun rejectedJwt(
        source: TestStatusResource,
        sourceData: SingleStatusResourceData,
        issuer: StatusListIssuer,
        id: String,
        subject: String?,
        exp: Long?,
        exception: String,
    ): TestStatusResource {
        val uri = "$baseUrl/$STATUS_CREDENTIAL_PATH/$id"
        return TestStatusResource(
            id = id,
            data = sourceData.copy(
                statusCredential = issuer.signJwt(sourceData.statusCredential, subject ?: uri, exp),
                holderCredential = holder(issuer.did, id, source.id, sourceData.holderCredential),
                valid = false,
                exception = exception,
            ),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun cwtResource(
        issuer: StatusListIssuer,
        id: String,
        subjectOverride: String?,
        exp: Long,
        valid: Boolean,
        exception: String?,
    ): TestStatusResource {
        val uri = "$baseUrl/$STATUS_CREDENTIAL_PATH/$id"
        val list = TokenStatusListCodec.encode(
            statuses = listOf(IndexedStatus(index = 0, value = 0u)),
            statusSize = 1,
            listSizeBytes = 16,
        )
        val payload = coseCompliantCbor.encodeToByteArray(
            CwtStatusListToken(
                sub = subjectOverride ?: uri,
                exp = exp,
                iat = 1_700_000_000,
                statusList = IETFStatusContent(size = 1, list = list),
            )
        )
        return TestStatusResource(
            id = id,
            data = SingleStatusResourceData(
                statusCredential = issuer.signCwt(payload).toHexString(),
                holderCredential = buildJsonObject {
                    put("vct", "https://credentials.example.com/identity_credential")
                    put("iss", issuer.did)
                    put("status", buildJsonObject {
                        put("status_list", buildJsonObject {
                            put("idx", 0)
                            put("uri", uri)
                        })
                    })
                },
                valid = valid,
                exception = exception,
                attribute = IETFStatusPolicyAttribute(value = 0u),
            ),
        )
    }

    private fun holder(did: String, id: String, sourceId: String, sourceHolder: JsonObject): JsonObject =
        withIssuer(
            Json.parseToJsonElement(Json.encodeToString(sourceHolder).replace(sourceId, id)).jsonObject,
            did,
        )

    private fun withIssuer(credential: JsonObject, did: String): JsonObject = JsonObject(
        credential + if ("vct" in credential) {
            mapOf("iss" to JsonPrimitive(did))
        } else {
            mapOf("issuer" to JsonPrimitive(did))
        }
    )

    private fun isIetfStatusJwt(jwt: String): Boolean = runCatching {
        val payload = json.parseToJsonElement(
            jwt.substringAfter('.').substringBefore('.').base64UrlDecode().decodeToString()
        ).jsonObject
        "status_list" in payload
    }.getOrDefault(false)

    private class StatusListIssuer {
        lateinit var key: JWKKey
            private set
        lateinit var did: String
            private set
        private lateinit var keyId: String

        suspend fun signJwt(jwt: String, statusListUri: String, exp: Long? = null): String {
            ensureReady()
            val payload = json.parseToJsonElement(
                jwt.substringAfter('.').substringBefore('.').base64UrlDecode().decodeToString()
            ).jsonObject
            val updated = payload + mapOf(
                "iss" to JsonPrimitive(did),
                "sub" to JsonPrimitive(statusListUri),
            ) + (exp?.let { mapOf("exp" to JsonPrimitive(it)) } ?: emptyMap())
            return signJwtPayload(JsonObject(updated))
        }

        suspend fun signCwt(payload: ByteArray): ByteArray {
            ensureReady()
            return CoseSign1.createAndSign(
                protectedHeaders = CoseHeaders(
                    algorithm = -8,
                    kid = keyId.encodeToByteArray(),
                ),
                payload = payload,
                signer = key.toCoseSigner(),
            ).toTagged()
        }

        private suspend fun ensureReady() {
            if (::key.isInitialized) return
            key = JWKKey.generate(KeyType.Ed25519)
            did = DidService.registerByKey("key", key).did
            keyId = DidService.resolveToCrypto2Keys(did).getOrThrow().single().id.value
        }

        private suspend fun signJwtPayload(payload: JsonObject): String {
            val header = buildJsonObject {
                put("alg", "EdDSA")
                put("typ", "statuslist+jwt")
                put("kid", keyId)
            }
            val headerB64 = Json.encodeToString(header).encodeToByteArray().encodeToBase64Url()
            val payloadB64 = Json.encodeToString(payload).encodeToByteArray().encodeToBase64Url()
            val signable = "$headerB64.$payloadB64"
            val signature = key.signRaw(signable.encodeToByteArray()).encodeToBase64Url()
            return "$signable.$signature"
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
private data class CwtStatusListToken(
    @CborLabel(2) val sub: String,
    @CborLabel(4) val exp: Long,
    @CborLabel(6) val iat: Long,
    @CborLabel(65533) val statusList: IETFStatusContent,
)
