import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.JsonObject
import kotlinx.uuid.UUID
import java.net.URLEncoder

class CategoryApi(private val client: HttpClient, val wallet: UUID) {
    suspend fun list(expectedSize: Int): List<JsonObject> =
        client.get("/wallet-api/wallet/$wallet/categories").expectSuccess().run {
            val result = body<List<JsonObject>>()
            assert(result.size == expectedSize)
            result
        }

    suspend fun add(name: String) =
        client.post("/wallet-api/wallet/$wallet/categories/${URLEncoder.encode(name, "utf-8")}/add").expectSuccess()

    suspend fun delete(name: String) =
        client.delete("/wallet-api/wallet/$wallet/categories/${URLEncoder.encode(name, "utf-8")}").expectSuccess()

    suspend fun rename(name: String, newName: String) =
        client.put(
            "/wallet-api/wallet/$wallet/categories/${URLEncoder.encode(name, "utf-8")}/rename/${
                URLEncoder.encode(
                    newName, "utf-8"
                )
            }"
        ).expectSuccess()
}
