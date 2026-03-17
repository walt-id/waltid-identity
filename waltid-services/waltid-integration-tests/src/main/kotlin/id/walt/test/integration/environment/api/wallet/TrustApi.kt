@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectSuccess
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class TrustRequest(
    val did: String,
    val credentialType: String,
    val egfUri: String,
    val isVerifier: Boolean = false,
)

@Serializable
data class TrustResponse(
    val trusted: Boolean,
)

class TrustApi(private val e2e: E2ETest, private val client: HttpClient) {

    suspend fun validateTrustRaw(walletId: Uuid, request: TrustRequest) =
        client.post("/wallet-api/wallet/$walletId/trust") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun validateTrust(walletId: Uuid, request: TrustRequest): TrustResponse =
        validateTrustRaw(walletId, request).let {
            it.expectSuccess()
            it.body<TrustResponse>()
        }

    suspend fun validateIssuerTrust(walletId: Uuid, did: String, credentialType: String, egfUri: String) =
        validateTrust(walletId, TrustRequest(did, credentialType, egfUri, isVerifier = false))

    suspend fun validateVerifierTrust(walletId: Uuid, did: String, credentialType: String, egfUri: String) =
        validateTrust(walletId, TrustRequest(did, credentialType, egfUri, isVerifier = true))
}
