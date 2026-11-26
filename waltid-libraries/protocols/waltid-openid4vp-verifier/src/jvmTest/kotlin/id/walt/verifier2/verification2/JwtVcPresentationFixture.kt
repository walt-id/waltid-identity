package id.walt.verifier2.verification2

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.verifier2.data.Verification2Session
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * A cross-device jwt_vc_json verification session with one Credential Query `ids`, and an issuer and
 * holder that sign credentials and presentations for it. Policies: audience and nonce checks on the
 * presentation, expiration on the credential.
 */
internal class JwtVcPresentationFixture(
    private val nonce: String,
    private val state: String? = null,
    private val multiple: Boolean = false,
) {
    val clientId = "redirect_uri:https://verifier.example/response"

    private lateinit var issuer: JWKKey
    private lateinit var holder: JWKKey

    suspend fun generateKeys() {
        issuer = JWKKey.generate(KeyType.secp256r1)
        holder = JWKKey.generate(KeyType.secp256r1)
    }

    fun session(): Verification2Session {
        val query = """{"id": "ids", "format": "jwt_vc_json", "multiple": $multiple, "meta": {"type_values": [["VerifiedID"]]}}"""
        val stateField = state?.let { """"state": "$it",""" } ?: ""
        return Json.decodeFromString(
            // language=JSON
            """
            {
              "id": "session-$nonce",
              "setup": {"flow_type": "cross_device", "core_flow": {"dcql_query": {"credentials": [$query]}}},
              "creationDate": "2026-10-19T08:00:00Z",
              "status": "IN_USE",
              "authorizationRequestUrl": "openid4vp://authorize",
              "requestMode": "REQUEST_URI_SIGNED",
              "authorizationRequest": {
                "response_type": "vp_token", "client_id": "$clientId", "response_mode": "direct_post",
                "response_uri": "https://verifier.example/response", "nonce": "$nonce", $stateField
                "client_metadata": {"vp_formats_supported": {"jwt_vc_json": {}}},
                "dcql_query": {"credentials": [$query]}
              },
              "policies": {
                "vp_policies": {"jwt_vc_json": ["jwt_vc_json/audience-check", "jwt_vc_json/nonce-check"]},
                "vc_policies": [{"policy": "expiration"}]
              }
            }
            """.trimIndent()
        )
    }

    suspend fun credential(name: String, expiresInDays: Int): String = issuer.signJws(
        buildJsonObject {
            put("iss", "did:example:issuer"); put("sub", "did:example:holder"); put("jti", "urn:uuid:$name")
            put("nbf", Clock.System.now().minus(10.days).epochSeconds)
            put("exp", Clock.System.now().plus(expiresInDays.days).epochSeconds)
            putJsonObject("vc") {
                putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                putJsonArray("type") { add("VerifiableCredential"); add("VerifiedID") }
                put("expirationDate", Clock.System.now().plus(expiresInDays.days).toString())
                putJsonObject("credentialSubject") { put("id", "did:example:holder"); put("name", name) }
            }
        }.toString().encodeToByteArray(),
        mapOf("typ" to JsonPrimitive("JWT")),
    )

    suspend fun presentation(credential: String, presentationNonce: String = nonce): String = holder.signJws(
        buildJsonObject {
            put("iss", "did:example:holder"); put("aud", clientId); put("nonce", presentationNonce)
            putJsonObject("vp") {
                putJsonArray("@context") { add("https://www.w3.org/2018/credentials/v1") }
                putJsonArray("type") { add("VerifiablePresentation") }
                putJsonArray("verifiableCredential") { add(credential) }
            }
        }.toString().encodeToByteArray(),
        mapOf("typ" to JsonPrimitive("JWT")),
    )
}
