package id.walt.credentials.keyresolver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtIssuerTest {
    @Test
    fun invalidRegisteredIssuerCannotFallBackToW3cIssuer() {
        for (invalid in listOf(JsonNull, JsonPrimitive(7), JsonPrimitive(true),
            buildJsonObject { put("id", "did:example:issuer") })) {
            assertFailsWith<IllegalArgumentException> {
                resolveJwtIssuer(buildJsonObject {
                    put("iss", invalid)
                    put("issuer", "did:example:issuer")
                })
            }
        }
    }

    @Test
    fun w3cIssuerObjectAndOuterIssuerAgree() {
        val payload = Json.parseToJsonElement(
            """{"iss":"did:example:issuer","vc":{"issuanceDate":"2026-01-01T00:00:00Z","issuer":{"id":"did:example:issuer"}}}"""
        ).jsonObject
        assertEquals("did:example:issuer", resolveJwtIssuer(payload))
        assertEquals(payload["vc"], w3cJwtPayload(payload))
    }
}
