package id.walt.credentials.keyresolver.resolvers

import id.walt.crypto.keys.jwk.JWKKey
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WellKnownKeyResolverTest {
    @Test
    fun `discovers current and legacy issuers and verifies their signatures with inline or referenced keys`() = runTest {
        for (path in listOf("", "/tenant", "/tenant/openid4vci")) {
            for (inline in listOf(true, false)) {
                withIssuerServer(path, metadata = { issuer, base ->
                    buildJsonObject {
                        put("issuer", issuer)
                        if (inline) put("jwks", jwks) else put("jwks_uri", "$base/keys")
                    }
                }) { issuer, requestedPaths ->
                    val resolved = WellKnownKeyResolver.resolveJwkFromWellKnown(
                        issuer, buildJsonObject { put("kid", "issuer-key") },
                    )
                    assertEquals(jwk, Json.parseToJsonElement(resolved.data.toByteArray().decodeToString()))
                    val signingKey = JWKKey.importJWK(JsonObject(jwk + ("d" to JsonPrimitive(
                        "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ",
                    ))).toString()).getOrThrow()
                    val signed = signingKey.signJws(
                        buildJsonObject { put("iss", issuer); put("vct", "urn:example:identity") }.toString().encodeToByteArray(),
                        mapOf("kid" to JsonPrimitive("issuer-key"), "typ" to JsonPrimitive("dc+sd-jwt")),
                    )
                    val verificationKey = JWKKey.importJWK(resolved.data.toByteArray().decodeToString()).getOrThrow()
                    assertTrue(verificationKey.verifyJws(signed).isSuccess)
                    assertEquals(
                        listOf("/.well-known/jwt-vc-issuer$path") + if (inline) emptyList() else listOf("/keys"),
                        requestedPaths.toList(),
                    )
                }
            }
        }
    }

    @Test
    fun `rejects missing or mismatched issuer before retrieving JWKS`() = runTest {
        for (returnedIssuer in listOf<String?>(null, "https://other.example", "trailing-slash")) {
            withIssuerServer("/tenant", metadata = { issuer, base ->
                buildJsonObject {
                    returnedIssuer?.let { put("issuer", if (it == "trailing-slash") "$issuer/" else it) }
                    put("jwks_uri", "$base/keys")
                }
            }) { issuer, requestedPaths ->
                val error = assertFailsWith<IllegalArgumentException> {
                    WellKnownKeyResolver.resolveJwkFromWellKnown(issuer, null)
                }
                assertEquals("JWT VC issuer metadata issuer must exactly match the credential issuer.", error.message)
                assertEquals(listOf("/.well-known/jwt-vc-issuer/tenant"), requestedPaths.toList())
            }
        }
    }

    private suspend fun withIssuerServer(
        path: String,
        metadata: (issuer: String, base: String) -> JsonObject,
        test: suspend (issuer: String, requestedPaths: CopyOnWriteArrayList<String>) -> Unit,
    ) {
        val previousLoopback = System.getProperty("walt.ssrf.allowLoopback")
        System.setProperty("walt.ssrf.allowLoopback", "true")
        val requestedPaths = CopyOnWriteArrayList<String>()
        var base = ""
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 0) {
            install(ContentNegotiation) { json() }
            routing {
                get("/.well-known/jwt-vc-issuer$path") {
                    requestedPaths.add("/.well-known/jwt-vc-issuer$path")
                    call.respond(metadata("$base$path", base))
                }
                get("/keys") {
                    requestedPaths.add("/keys")
                    call.respond(jwks)
                }
            }
        }
        try {
            server.start(wait = false)
            base = "http://127.0.0.1:${server.engine.resolvedConnectors().single().port}"
            test("$base$path", requestedPaths)
        } finally {
            server.stop(0, 0)
            if (previousLoopback == null) System.clearProperty("walt.ssrf.allowLoopback")
            else System.setProperty("walt.ssrf.allowLoopback", previousLoopback)
        }
    }

    private companion object {
        val jwk = Json.parseToJsonElement(
            """{"kty":"EC","crv":"P-256","kid":"issuer-key","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"}""",
        ).jsonObject
        val jwks = buildJsonObject { put("keys", JsonArray(listOf(jwk))) }
    }
}
