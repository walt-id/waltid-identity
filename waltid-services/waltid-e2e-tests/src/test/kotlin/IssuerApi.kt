import E2ETestWebService.test
import id.walt.issuer.issuance.IssuanceRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class IssuerApi(private val client: HttpClient) {
    suspend fun issue(request: IssuanceRequest, output: ((String) -> Unit)? = null) =
        test("/openid4vc/jwt/issue - issue credential") {
            client.post("/openid4vc/jwt/issue") {
                setBody(request)
            }.expectSuccess().apply {
                output?.invoke(body<String>())
            }
        }
}