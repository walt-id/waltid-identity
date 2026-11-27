package id.walt.verifier2.verification2

import id.walt.credentials.formats.W3C11
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.dcql.models.CredentialQuery
import id.walt.did.dids.DidService
import id.walt.verifier2.data.Verification2Session
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class JwtVcJsonHolderBindingConfigurationTest {
    @Test
    fun requiredBindingSurvivesCustomPolicyReplacementAndOptOutIsExplicit() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", key).did
        val kid = DidService.resolveAuthenticationMethodId(did, key.getKeyId())
        val vp = JsonObject(emptyMap())
        val payload = JsonObject(mapOf("iss" to JsonPrimitive(did), "vp" to vp))
        val jwt = key.signJws(payload.toString().encodeToByteArray(), mapOf("kid" to JsonPrimitive(kid)))
        val credential = W3C11(credentialData = JsonObject(emptyMap()), subject = did, signature = null, signed = jwt)
        val presentation = JwtVcJsonPresentation(jwt, payload, did, null, null, vp, listOf(credential))
        for (required in listOf(true, false)) {
            val query = Json.decodeFromString<CredentialQuery>("""{
                "id":"identity","format":"jwt_vc_json","meta":{"type_values":[["VerifiedID"]]},"require_cryptographic_holder_binding":$required
            }""")
            val encodedQuery = Json.encodeToString(query)
            val session = Json.decodeFromString<Verification2Session>("""{
                "id":"binding-config",
                "setup":{"flow_type":"cross_device","core_flow":{"dcql_query":{"credentials":[$encodedQuery]}}},
                "creationDate":"2026-11-23T00:00:00Z","status":"IN_USE",
                "authorizationRequestUrl":"openid4vp://authorize","requestMode":"REQUEST_URI_SIGNED",
                "authorizationRequest":{
                    "response_type":"vp_token","client_id":"redirect_uri:https://verifier.example/response",
                    "response_mode":"direct_post","response_uri":"https://verifier.example/response",
                    "nonce":"nonce","dcql_query":{"credentials":[$encodedQuery]},
                    "client_metadata":{"vp_formats_supported":{"jwt_vc_json":{}}}
                },
                "policies":{"vp_policies":{"jwt_vc_json":[]}}
            }""")
            val results = PresentationVerificationEngine.verifyAllPresentations(
                mapOf((jwt to query) to presentation), session, Clock.System.now(),
            ).getValue("identity")
            assertEquals(required, "jwt_vc_json/holder-binding" in results)
            assertTrue(results.values.all { it.success })
        }
    }
}
