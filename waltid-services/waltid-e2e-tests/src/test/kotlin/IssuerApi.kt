import id.walt.issuer.issuance.IssuanceRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class IssuerApi(private val client: HttpClient) {
    suspend fun jwt(request: IssuanceRequest) = issue(
        "/openid4vc/jwt/issue",
        request,
    )

    suspend fun sdjwt(request: IssuanceRequest) = issue(
        "/openid4vc/sdjwt/issue",
        request,
    )

    suspend fun mdoc(request: IssuanceRequest) = issue(
        "/openid4vc/mdoc/issue",
        request,
    )

    suspend fun issueJwtBatch(requests: List<IssuanceRequest>) = issue(
        "/openid4vc/jwt/issueBatch",
        *requests.toTypedArray(),
    )

    suspend fun issueSdJwtBatch(requests: List<IssuanceRequest>) = issue(
        "/openid4vc/sdjwt/issueBatch",
        *requests.toTypedArray(),
    )

    private suspend fun issue(url: String, vararg requests: IssuanceRequest) = client.post(url) {
        setBody(
            when (requests.size) {
                1 -> requests.first()
                else -> requests.toList()
            }
        )
    }.expectSuccess().run {
        body<String>()
    }
}
