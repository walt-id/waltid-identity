import E2ETestWebService.test
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.JsonObject
import kotlinx.uuid.UUID

class CredentialsApi(private val client: HttpClient) {
    suspend fun list(wallet: UUID, expectedSize: Int) =
        test("/wallet-api/wallet/{wallet}/credentials - list credentials") {
            client.get("/wallet-api/wallet/$wallet/credentials").expectSuccess().apply {
                val credentials = body<List<JsonObject>>()
                assert(credentials.size == expectedSize) { "should not have any credentials yet" }
            }
        }
}