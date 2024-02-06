package id.walt.webwallet.service.trust

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class EntraIssuerTrustValidationService(private val http: HttpClient) : TrustValidationService {
    private val baseUrl = "https://api-dev.tel-platform.online"
    private val governanceRecordPath = "trust/governanceRecord/%s"
    private val trustedRecordPath = "/trust/trustRecord/query/issuer?identifier=%s&credentialType=%s&egfURI=%s"
    override suspend fun validate(did: String, type: String): Boolean = when (http.get(
        String.format(
            "$baseUrl/$trustedRecordPath", did, type, String.format("$baseUrl/$governanceRecordPath", did)
        )
    ).status) {
        HttpStatusCode.OK -> true
        else -> false
    }

    data class SuccessResponse(
        val name: String,
        val identifier: String,
        val createdAt: String,
    )

    data class FailResponse(
        val type: String,
        val title: String,
        val status: String,
        val detail: String,
        val instance: String,
    )
}