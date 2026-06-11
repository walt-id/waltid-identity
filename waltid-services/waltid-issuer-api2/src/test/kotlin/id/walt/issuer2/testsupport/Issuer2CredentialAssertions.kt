package id.walt.issuer2.testsupport

import id.walt.issuer2.domain.IssuanceSession
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun assertBearerAccessToken(tokenResponse: TokenRequestBuilder.TokenResponse) {
    assertTrue(tokenResponse.token_type.equals("bearer", ignoreCase = true))
    assertTrue(tokenResponse.access_token.isNotBlank())
}

suspend fun assertSessionStatus(
    client: HttpClient,
    sessionId: String,
    expectedStatus: String,
) {
    val response = client.get("/issuer2/sessions/$sessionId")
    assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

    // ACTIVE is the default enum value and is omitted from JSON when encodeDefaults=false.
    // Decode the session so Kotlin serialization applies the same default the service uses.
    val session = response.body<IssuanceSession>()
    assertEquals(expectedStatus, session.status.name)
}

fun assertJwtVcJsonCredentialPayload(credentialPayload: JsonObject): String {
    val issuedCredential = assertNotNull(
        credentialPayload["credentials"]
            ?.jsonArray
            ?.single()
            ?.jsonObject
            ?.get("credential")
            ?.jsonPrimitive
            ?.content
    )
    assertTrue(issuedCredential.split(".").size >= 3)
    return issuedCredential
}
