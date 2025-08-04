package id.walt.test.integration

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.sdjwt.SDMap
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun String.expectLooksLikeJwt(): String =
    also { assertTrue(startsWith("ey") && count { it == '.' } == 2, "Does not look like JWT") }

val expectSuccess: suspend HttpResponse.() -> HttpResponse = {
    assertTrue(
        this.status.isSuccess(),
        "HTTP status is non-successful for response: $this, body is ${this.bodyAsText()}"
    )
    this
}

val expectError: suspend HttpResponse.() -> HttpResponse = {
    assertFalse(
        this.status.isSuccess(),
        "HTTP status is successful but expected error - response: $this, body is ${this.bodyAsText()}"
    )
    this
}


val expectRedirect: HttpResponse.() -> HttpResponse = {
    assertTrue(this.status == HttpStatusCode.Found, "HTTP status is non-successful")
    this
}

val expectFailure: HttpResponse.() -> HttpResponse = {
    assertTrue(!status.isSuccess(), "HTTP status is successful")
    this
}

fun randomString(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
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

fun loadJsonResource(resourceName: String): JsonObject {
    return Json.decodeFromString<JsonObject>(loadResource(resourceName))
}

fun JsonObject.toSdMap(): SDMap {
    return Json.decodeFromJsonElement<SDMap>(this)
}

