package id.walt.verifier2.handlers.sessioncreation

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.verifier2.data.CrossDeviceFlowSetup
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class VerificationSessionCreatorTest {

    @Test
    fun signedAuthorizationRequestContainsWalletAudience() = runTest {
        val setup = (CrossDeviceFlowSetup.EXAMPLE_SDJWT_PID as CrossDeviceFlowSetup).let {
            it.copy(core = it.core.copy(signedRequest = true))
        }
        val key = JWKKey.generate(KeyType.secp256r1)

        val session = VerificationSessionCreator.createVerificationSession(
            setup = setup,
            clientId = "x509_san_dns:test123",
            urlPrefix = "https://verifier.example/verification-session",
            urlHost = "https://wallet.example/authorize",
            key = key,
        )

        val requestPayload = session.signedAuthorizationRequestJwt!!.split(".")[1]
        val decodedPayload = Base64.getUrlDecoder().decode(requestPayload).decodeToString()
        val payloadJson = Json.parseToJsonElement(decodedPayload).jsonObject

        assertEquals("https://self-issued.me/v2", payloadJson["aud"]?.jsonPrimitive?.content)
    }
}
