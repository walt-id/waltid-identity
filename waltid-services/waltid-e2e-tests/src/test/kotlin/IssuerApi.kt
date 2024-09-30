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
        name = "/openid4vc/sdjwt/issue - issue sdjwt credential",
        url = "/openid4vc/sdjwt/issue",
        request = request,
        output = output,
    )

    suspend fun mdoc(request: IssuanceRequest, output: ((String) -> Unit)? = null) = issue(
        name = "/openid4vc/mdoc/issue - issue mdoc credential",
        url = "/openid4vc/mdoc/issue",
        request = request,
        output = output,
    )

    suspend fun issueJwtBatch(requests: List<IssuanceRequest>, output: ((String) -> Unit)? = null) =
        test("/openid4vc/jwt/issueBatch - issue jwt credential batch") {
            client.post("/openid4vc/jwt/issueBatch") {
                setBody(requests)
            }.expectSuccess().apply {
                output?.invoke(body<String>())
            }
        }

    suspend fun issueSdJwtBatch(requests: List<IssuanceRequest>, output: ((String) -> Unit)? = null) =
        test("/openid4vc/sdjwt/issueBatch - issue sd-jwt credential batch") {
            client.post("/openid4vc/sdjwt/issueBatch") {
                setBody(requests)
            }.expectSuccess().apply {
                output?.invoke(body<String>())
            }
        }

    private suspend fun issue(name: String, url: String, request: IssuanceRequest, output: ((String) -> Unit)? = null) =
        test(name) {
            client.post(url) {
                setBody(request)
            }.expectSuccess().apply {
                output?.invoke(body<String>())
            }
        }
}