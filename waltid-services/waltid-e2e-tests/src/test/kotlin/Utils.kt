import id.walt.commons.web.plugins.httpJson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

fun String.expectLooksLikeJwt(): String =
    also { assert(startsWith("ey") && count { it == '.' } == 2) { "Does not look like JWT" } }

fun HttpResponse.expectSuccess(): HttpResponse = also {
    assert(status.isSuccess()) { "HTTP status is non-successful" }
}

//todo: temporary
fun HttpResponse.expectFailure(): HttpResponse = also {
    assert(!status.isSuccess()) { "HTTP status is successful" }
}

fun JsonElement.tryGetData(key: String): JsonElement? = key.split('.').let {
    var element: JsonElement? = this
    for (i in it) {
        element = when (element) {
            is JsonObject -> element[i]
            is JsonArray -> element.firstOrNull {
                it.jsonObject.containsKey(i)
            }?.let {
                it.jsonObject[i]
            }

            else -> element?.jsonPrimitive
        }
    }
    element
}

fun testHttpClient(token: String? = null) = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(httpJson)
    }
    install(DefaultRequest) {
        contentType(ContentType.Application.Json)
        host = "127.0.0.1"
        port = 22222

        if (token != null) bearerAuth(token)
    }
    install(Logging) {
        level = LogLevel.ALL
    }
}

fun JsonObject.getString(key: String) = this[key]?.jsonPrimitive?.content
fun JsonElement?.asString() = this?.jsonPrimitive?.content
