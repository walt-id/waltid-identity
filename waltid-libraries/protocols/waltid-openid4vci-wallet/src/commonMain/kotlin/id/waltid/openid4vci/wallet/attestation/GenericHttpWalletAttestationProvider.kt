package id.waltid.openid4vci.wallet.attestation

import id.walt.crypto.keys.Key
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val WALLET_INSTANCE_ATTESTATION_FIELD = "walletInstanceAttestation"
const val PUBLIC_JWK_PLACEHOLDER = "{{public_jwk}}"

class GenericHttpWalletAttestationProvider(
    private val attesterUrl: String,
    private val requestBodyTemplate: JsonObject,
    private val httpClient: HttpClient = HttpClient(),
) : WalletAttestationProvider {

    private val json = Json { ignoreUnknownKeys = true }

    init {
        require(attesterUrl.isNotBlank()) { "attesterUrl must not be blank" }
    }

    override suspend fun getAttestationJwt(instanceKey: Key, clientId: String): String {
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        val publicJwk = json.parseToJsonElement(instanceKey.getPublicKey().exportJWK()).jsonObject
        val requestBody = requestBodyTemplate.render(publicJwk)

        val response = httpClient.post(attesterUrl) {
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        if (!response.status.isSuccess()) {
            error("Wallet attestation request failed: ${response.status}")
        }

        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject[WALLET_INSTANCE_ATTESTATION_FIELD]
            ?.jsonPrimitive
            ?.content
            ?.takeIf { it.isNotBlank() }
            ?: error("Wallet attestation response must contain a non-empty '$WALLET_INSTANCE_ATTESTATION_FIELD' field")
    }

    private fun JsonElement.render(publicJwk: JsonObject): JsonElement =
        when (this) {
            is JsonObject -> JsonObject(mapValues { (_, value) -> value.render(publicJwk) })
            is JsonArray -> JsonArray(map { it.render(publicJwk) })
            is JsonPrimitive ->
                if (this == JsonPrimitive(PUBLIC_JWK_PLACEHOLDER)) {
                    publicJwk
                } else {
                    this
                }
        }
}
