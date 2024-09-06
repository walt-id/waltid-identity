import E2ETestWebService.test
import id.walt.issuer.issuance.IssuanceRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class IssuerApi(private val client: HttpClient) {
    suspend fun issue(request: IssuanceRequest, output: ((String) -> Unit)? = null) =
        test("/openid4vc/jwt/issue - issue jwt credential") {
            client.post("/openid4vc/jwt/issue") {
                setBody(request)
            }.expectSuccess().apply {
                output?.invoke(body<String>())
            }
        }

    suspend fun issueJwtBatch(requests: List<IssuanceRequest>, output: ((String) -> Unit)? = null) =
        test("/openid4vc/jwt/issueBatch - issue jwt credential batch") {
            client.post("/openid4vc/jwt/issueBatch") {
                setBody(requests)
            }.expectSuccess().apply {
                output?.invoke(body<String>())
            }
        }

    suspend fun issueSdJwt(request: IssuanceRequest, output: ((String) -> Unit)? = null) =
        test("/openid4vc/sdjwt/issue - issue sd-jwt credential") {
            client.post("/openid4vc/sdjwt/issue") {
                setBody(request)
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

    suspend fun issueMDoc(request: IssuanceRequest, output: ((String) -> Unit)? = null) =
        test("/openid4vc/mdoc/issue - issue mdoc credential") {
            client.post("/openid4vc/mdoc/issue") {
                setBody(request)
            }.expectSuccess().apply {
                output?.invoke(body<String>())
            }
        }
}