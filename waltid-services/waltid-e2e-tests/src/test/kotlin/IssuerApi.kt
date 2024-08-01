import E2ETestWebService.test
import id.walt.issuer.issuance.IssuanceRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class IssuerApi(private val client: HttpClient) {
    suspend fun jwt(request: IssuanceRequest, output: ((String) -> Unit)? = null) = issue(
        name = "/openid4vc/jwt/issue - issue jwt credential",
        url = "/openid4vc/jwt/issue",
        request = request,
        output = output,
    )

    suspend fun sdjwt(request: IssuanceRequest, output: ((String) -> Unit)? = null) = issue(
        name = "/openid4vc/jwt/issue - issue sdjwt credential",
        url = "/openid4vc/sdjwt/issue",
        request = request,
        output = output,
    )

    private suspend fun issue(name: String, url: String, request: IssuanceRequest, output: ((String) -> Unit)? = null) =
        test(name) {
            client.post(url) {
                setBody(request)
            }.expectSuccess().apply {
                output?.invoke(body<String>())
            }
        }
}