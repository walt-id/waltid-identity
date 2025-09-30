package id.walt.test.integration.environment.api

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ResponseError(
    val httpStatusCode: HttpStatusCode,
    val responseBody: JsonObject
) {
    companion object {
        suspend fun of(response: HttpResponse): ResponseError {
            return ResponseError(
                response.status,
                response.body<JsonObject>())
        }
    }

    val errorMessage: String?
        get() = responseBody["errorMessage"]?.jsonPrimitive?.content
}