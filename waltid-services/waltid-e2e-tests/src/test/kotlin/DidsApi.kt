@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.testing.E2ETest
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.web.model.DidImportRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class DidsApi(private val e2e: E2ETest, private val client: HttpClient) {
    private val didRegexPattern = "^^did:%s:\\S+\$"
    suspend fun list(
        wallet: Uuid,
        expectedDefault: DefaultDidOption,
        size: Int? = null,
        output: ((List<WalletDid>) -> Unit)? = null,
    ) =
        e2e.test("/wallet-api/wallet/{wallet}/dids - list DIDs") {
            client.get("/wallet-api/wallet/$wallet/dids").expectSuccess().apply {
                val dids = body<List<WalletDid>>()
                assertTrue(dids.isNotEmpty()) { "Wallet has no DIDs!" }
                size?.let { assertTrue(dids.size == it) { "Wallet has invalid number of DIDs!" } }
                expectedDefault.whenNone { assertTrue(dids.none { it.default }) }
                expectedDefault.whenAny { assertNotNull(dids.single { it.default }) }
                expectedDefault.whenSome { did -> assertTrue(dids.single { it.did == did }.default) }
                output?.invoke(dids)
            }
        }

    suspend fun get(wallet: Uuid, did: String) = e2e.test("/wallet-api/wallet/{wallet}/dids/{did} - show specific DID") {
        client.get("/wallet-api/wallet/$wallet/dids/$did").expectSuccess().apply {
            val response = body<JsonObject>()
            assertTrue(response["id"]?.jsonPrimitive?.content == did)
            println("DID document: $response")
        }
    }

    suspend fun delete(wallet: Uuid, did: String) = e2e.test("/wallet-api/wallet/{wallet}/dids/{did} - delete did") {
        client.delete("/wallet-api/wallet/$wallet/dids/$did").expectSuccess()
    }

    suspend fun default(wallet: Uuid, did: String) =
        e2e.test("/wallet-api/wallet/{wallet}/dids/default - set default did") {
            client.post("/wallet-api/wallet/$wallet/dids/default?did=$did").expectSuccess()
        }

    suspend fun create(wallet: Uuid, payload: DidCreateRequest, output: ((String) -> Unit)? = null) =
        e2e.test("/wallet-api/wallet/{wallet}/dids/create/${payload.method} - create did:${payload.method}") {
            client.post("/wallet-api/wallet/$wallet/dids/create/${payload.method}") {
                url {
                    payload.toMap().onEach {
                        parameters.append(it.key, it.value)
                    }
                }
            }.expectSuccess().apply {
                val did = body<String>()
                assertTrue(String.format(didRegexPattern, payload.method).toRegex().matches(did))
                output?.invoke(did)
            }
        }

    suspend fun importDid(wallet: Uuid , didImportRequest : DidImportRequest) =
        e2e.test("/wallet-api/wallet/{wallet}/dids/import - import did") {
            client.post("/wallet-api/wallet/$wallet/dids/import") {
                setBody(didImportRequest)
            }.expectSuccess().apply {
                val did = body<String>()
                assertTrue(String.format(didRegexPattern, did.substringAfter("did:").substringBefore(":")).toRegex().matches(did))
            }
        }

    data class DidCreateRequest(
        val method: String,
        val keyId: String? = null,
        val alias: String? = null,
        val options: Map<String, Any> = emptyMap(),
    ) {
        fun toMap() = mutableMapOf<String, String>().apply {
            keyId?.run { put("keyId", this) }
            alias?.run { put("alias", this) }
            putAll(options.mapValues { it.toString() })
        }
    }

    sealed class DefaultDidOption {
        @ConsistentCopyVisibility
        data class Some internal constructor(val value: String) : DefaultDidOption()
        data object None : DefaultDidOption()
        data object Any : DefaultDidOption()

        fun whenSome(action: (String) -> Unit): DefaultDidOption {
            return when (this) {
                is Some -> apply { action(value) }
                is None -> this
                is Any -> this
            }
        }

        fun whenNone(action: () -> Unit): DefaultDidOption {
            return when (this) {
                is Some -> this
                is None -> apply { action() }
                is Any -> this
            }
        }

        fun whenAny(action: () -> Unit): DefaultDidOption {
            return when (this) {
                is Some -> this
                is None -> this
                is Any -> apply { action() }
            }
        }
    }
}
