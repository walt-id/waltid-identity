package id.walt.test.integration

import io.ktor.client.statement.*
import io.ktor.http.*

fun String.expectLooksLikeJwt(): String =
    also { assert(startsWith("ey") && count { it == '.' } == 2) { "Does not look like JWT" } }

val expectSuccess: suspend HttpResponse.() -> HttpResponse = {
    assert(this.status.isSuccess()) { "HTTP status is non-successful for response: $this, body is ${this.bodyAsText()}" }; this
}

val expectRedirect: HttpResponse.() -> HttpResponse = {
    assert(this.status == HttpStatusCode.Found) { "HTTP status is non-successful" }; this
}

val expectFailure: HttpResponse.() -> HttpResponse = {
    assert(!status.isSuccess()) { "HTTP status is successful" }; this
}

