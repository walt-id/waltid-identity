import id.walt.issuer.issuance.IssuanceRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class IssuerApi(private val client: HttpClient) {
    suspend fun jwt(request: IssuanceRequest) = issue("/openid4vc/jwt/issue", request)
    suspend fun sdjwt(request: IssuanceRequest) = issue("/openid4vc/sdjwt/issue", request)

    private suspend fun issue(url: String, request: IssuanceRequest) =
        client.post(url) {
            setBody(request)
        }.expectSuccess().run {
            body<String>()
        }
}
