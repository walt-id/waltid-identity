import E2ETestWebService.test
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.JsonObject
import kotlinx.uuid.UUID
import java.net.URLEncoder

class CategoryApi(private val client: HttpClient) {
    suspend fun list(wallet: UUID, expectedSize: Int, output: ((List<JsonObject>) -> Unit)? = null) =
        test("/wallet-api/wallet/{wallet}/categories - list categories") {
            client.get("/wallet-api/wallet/$wallet/categories").expectSuccess().apply {
                val result = body<List<JsonObject>>()
                assert(result.size == expectedSize)
                output?.invoke(result)
            }
        }

    suspend fun add(wallet: UUID, name: String) =
        test("/wallet-api/wallet/{wallet}/categories/{name}/add - add category") {
            client.post("/wallet-api/wallet/$wallet/categories/${URLEncoder.encode(name, "utf-8")}/add").expectSuccess()
        }

    suspend fun delete(wallet: UUID, name: String) =
        test("/wallet-api/wallet/{wallet}/categories/{name} - delete category") {
            client.delete("/wallet-api/wallet/$wallet/categories/${URLEncoder.encode(name, "utf-8")}").expectSuccess()
        }

    suspend fun rename(wallet: UUID, name: String, newName: String) =
        test("/wallet-api/wallet/{wallet}/categories/{name}/rename/{newName} - rename category") {
            client.put(
                "/wallet-api/wallet/$wallet/categories/${URLEncoder.encode(name, "utf-8")}/rename/${
                    URLEncoder.encode(
                        newName, "utf-8"
                    )
                }"
            ).expectSuccess()
        }
}