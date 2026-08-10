package id.walt.policies2.vc.status.status

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.jose.CompactJws
import id.walt.did.dids.DidService
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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

            DidService.minimalInit()
            credentials = signStatusCredentials(
                resourceReader.readResourcesBySubfolder(
                    "status",
                    placeholderValue = "$baseUrl/$STATUS_CREDENTIAL_PATH"
                )
            )
            credentials = credentials + ("signature" to listOf(tamperedStatusCredential(credentials)))
            credentials = credentials + ("authorization" to listOf(unauthorizedStatusCredential(credentials)))

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
        // match TestStatusResource id
        resources.find { it.id == targetId }?.let { testResource ->
            when (val data = testResource.data) {
                is MultiStatusResourceData -> data.statusCredential.firstOrNull()?.content
                is SingleStatusResourceData -> data.statusCredential
            }
        } ?: // match MultiStatusResourceData id
        resources
            .mapNotNull { it.data as? MultiStatusResourceData }
            .flatMap { it.statusCredential }
            .find { it.id == targetId }
            ?.content

    private suspend fun signStatusCredentials(
        resources: Map<String, List<TestStatusResource>>
    ): Map<String, List<TestStatusResource>> {
        val key = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", key).did
        val keyId = DidService.resolveToCrypto2Keys(did).getOrThrow().single().id.value
        return resources.mapValues { (_, entries) ->
            entries.map { resource ->
                resource.copy(
                    data = when (val data = resource.data) {
                        is SingleStatusResourceData -> data.copy(
                            statusCredential = signStatusCredential(
                                data.statusCredential,
                                key,
                                did,
                                keyId,
                                "$baseUrl/$STATUS_CREDENTIAL_PATH/${resource.id}",
                            ),
                            holderCredential = withIssuer(data.holderCredential, did),
                        )
                        is MultiStatusResourceData -> data.copy(
                            statusCredential = data.statusCredential.map { credential ->
                                credential.copy(
                                    content = signStatusCredential(
                                        credential.content,
                                        key,
                                        did,
                                        keyId,
                                        "$baseUrl/$STATUS_CREDENTIAL_PATH/${credential.id}",
                                    )
                                )
                            },
                            holderCredential = withIssuer(data.holderCredential, did),
                        )
                    }
                )
            }
        }
    }

    private suspend fun signStatusCredential(
        jwt: String,
        key: JWKKey,
        did: String,
        keyId: String,
        statusListUri: String,
    ): String {
        val decoded = CompactJws.decodeUnverified(jwt)
        val payload = Json.parseToJsonElement(decoded.payload.decodeToString()).jsonObject
        val updatedPayload = payload + ("iss" to JsonPrimitive(did)) +
            if ("status_list" in payload) mapOf("sub" to JsonPrimitive(statusListUri)) else emptyMap()
        return key.signJws(
            plaintext = Json.encodeToString(JsonObject(updatedPayload)).encodeToByteArray(),
            headers = JsonObject(
                decoded.protectedHeader + mapOf(
                    "alg" to JsonPrimitive("EdDSA"),
                    "kid" to JsonPrimitive(keyId),
                )
            ),
        )
    }

    private fun withIssuer(credential: JsonObject, did: String): JsonObject = JsonObject(
        credential + if ("vct" in credential) {
            mapOf("iss" to JsonPrimitive(did))
        } else {
            mapOf("issuer" to JsonPrimitive(did))
        }
    )

    private fun tamperedStatusCredential(resources: Map<String, List<TestStatusResource>>): TestStatusResource {
        val source = resources.values.flatten().first { it.data is SingleStatusResourceData && it.data.valid }
        val sourceData = source.data as SingleStatusResourceData
        val id = "invalid-status-list-signature"
        val holderCredential = Json.parseToJsonElement(
            Json.encodeToString(sourceData.holderCredential).replace(source.id, id)
        ).jsonObject
        val parts = sourceData.statusCredential.split('.')
        val signature = parts[2]
        val tamperedSignature = (if (signature.first() == 'A') 'B' else 'A') + signature.drop(1)
        return TestStatusResource(
            id = id,
            data = sourceData.copy(
                statusCredential = "${parts[0]}.${parts[1]}.$tamperedSignature",
                holderCredential = holderCredential,
                valid = false,
                exception = "signature verification failed",
            ),
        )
    }

    private suspend fun unauthorizedStatusCredential(
        resources: Map<String, List<TestStatusResource>>
    ): TestStatusResource {
        val source = resources.values.flatten().first { it.data is SingleStatusResourceData && it.data.valid }
        val sourceData = source.data as SingleStatusResourceData
        val id = "unauthorized-status-list-signer"
        val statusListUri = "$baseUrl/$STATUS_CREDENTIAL_PATH/$id"
        val holderCredential = Json.parseToJsonElement(
            Json.encodeToString(sourceData.holderCredential).replace(source.id, id)
        ).jsonObject
        val attackerKey = JWKKey.generate(KeyType.Ed25519)
        val attackerDid = DidService.registerByKey("key", attackerKey).did
        val attackerKeyId = DidService.resolveToCrypto2Keys(attackerDid).getOrThrow().single().id.value
        return TestStatusResource(
            id = id,
            data = sourceData.copy(
                statusCredential = signStatusCredential(
                    sourceData.statusCredential,
                    attackerKey,
                    attackerDid,
                    attackerKeyId,
                    statusListUri,
                ),
                holderCredential = holderCredential,
                valid = false,
                exception = "not authorized",
            ),
        )
    }
}
