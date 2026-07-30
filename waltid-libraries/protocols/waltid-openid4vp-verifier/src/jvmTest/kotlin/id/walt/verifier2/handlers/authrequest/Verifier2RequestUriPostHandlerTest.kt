@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.authrequest

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.handlers.authrequest.Verifier2RequestUriPostHandler.respondRequestUriPost
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreator
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Verifier2RequestUriPostHandlerTest {

    @Test
    fun `post request re-signs the object with the supplied wallet nonce`() = runTest {
        val signingKey = testSigningKey()
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(core = GeneralFlowConfig(signedRequest = true)),
            clientId = "verifier.example.com",
            urlPrefix = "https://verifier.example.com/verification-session",
            urlHost = "openid4vp://authorize",
            key = signingKey.key,
        )
        val events = mutableListOf<SessionEvent>()

        testApplication {
            application {
                routing {
                    post("/request") {
                        call.respondRequestUriPost(
                            verificationSession = session,
                            updateSessionCallback = { updatedSession, event, block ->
                                events += event
                                updatedSession.block()
                            },
                            resolveSigningKey = { signingKey.key },
                        )
                    }
                }
            }

            val response = client.post("/request") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("wallet_nonce=wallet-bound-nonce")
            }

            assertEquals(200, response.status.value)
            assertEquals("application/oauth-authz-req+jwt", response.contentType()?.withoutParameters()?.toString())
            val requestObject = response.bodyAsText()
            signingKey.key.getPublicKey().verifyJws(requestObject).getOrThrow()
            val payload = Json.parseToJsonElement(
                requestObject.split(".")[1].decodeFromBase64Url().decodeToString(),
            ).jsonObject
            assertEquals("wallet-bound-nonce", payload["wallet_nonce"]?.jsonPrimitive?.content)
        }

        assertEquals(listOf(SessionEvent.authorization_request_requested), events)
        assertNotNull(session.signedAuthorizationRequestJwt)
    }

    private fun testSigningKey() = DirectSerializedKey(
        KeyManager.resolveSerializedKeyBlocking(
            """{"type":"jwk","jwk":{"kty":"EC","d":"AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA","crv":"P-256","kid":"_nd-T2YRYLSmuKkJZlRI641zrCIJLTpiHeqMwXuvdug","x":"G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0","y":"VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4"}}"""
        )
    )
}
