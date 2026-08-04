@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.sessioncreation

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VerificationSessionCreatorSignedRequestTest {

    private val azureStyleKid = "https://example.vault.azure.net/keys/verifier-signing-key/abc123"

    private suspend fun keyWithAzureStyleKid(): JWKKey {
        val key = JWKKey.generate(KeyType.secp256r1)
        val jwk = JsonObject(key.exportJWKObject() + ("kid" to JsonPrimitive(azureStyleKid)))
        return JWKKey.importJWK(jwk.toString()).getOrThrow()
    }

    private fun signedSetupWithoutKey() = CrossDeviceFlowSetup(
        core = GeneralFlowConfig(
            dcqlQuery = DcqlQuery(
                credentials = listOf(
                    CredentialQuery(
                        id = "example_openbadge_jwt_vc",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(typeValues = listOf(listOf("OpenBadgeCredential"))),
                    )
                )
            ),
            signedRequest = true,
        )
    )

    private fun decodeJwtPart(jwt: String, index: Int) =
        Json.parseToJsonElement(jwt.split(".")[index].decodeFromBase64Url().decodeToString()).jsonObject

    @Test
    fun `did key kid uses multibase fragment not vault url`() = runTest {
        val key = keyWithAzureStyleKid()
        val did = "did:key:zDnaerx9CtbPJEvXAZVZtX4cK6e3vY8dYx9mK9xqK9xqK9x"
        val session = VerificationSessionCreator.createVerificationSession(
            setup = signedSetupWithoutKey(),
            clientId = "decentralized_identifier:$did",
            urlPrefix = "https://verifier.example.com/v1/org.tenant.verifier/verifier2-service-api",
            urlHost = "openid4vp://authorize",
            key = key,
        )

        val jwt = assertNotNull(session.signedAuthorizationRequestJwt)
        val kid = decodeJwtPart(jwt, 0)["kid"]!!.jsonPrimitive.content
        assertEquals("$did#${did.removePrefix("did:key:")}", kid)
        assertFalse(kid.contains("vault.azure.net"))
        assertFalse(kid.contains("https://"))
    }

    @Test
    fun `non did-key client id avoids https key id fragment`() = runTest {
        val key = keyWithAzureStyleKid()
        val did = "did:web:verifier.example.com"
        val session = VerificationSessionCreator.createVerificationSession(
            setup = signedSetupWithoutKey(),
            clientId = "decentralized_identifier:$did",
            urlPrefix = "https://verifier.example.com/v1/org.tenant.verifier/verifier2-service-api",
            urlHost = "openid4vp://authorize",
            key = key,
        )

        val jwt = assertNotNull(session.signedAuthorizationRequestJwt)
        val kid = decodeJwtPart(jwt, 0)["kid"]!!.jsonPrimitive.content
        val expectedThumbprint = key.getPublicKey().getThumbprint()
        assertEquals("$did#$expectedThumbprint", kid)
        assertFalse(kid.contains("vault.azure.net"))
    }

    @Test
    fun `create-time signing key is persisted on session setup for request_uri post`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val setup = signedSetupWithoutKey()
        assertNull(setup.core.key)

        val session = VerificationSessionCreator.createVerificationSession(
            setup = setup,
            clientId = "decentralized_identifier:did:key:zDnaeTest",
            urlPrefix = "https://verifier.example.com/v1/org.tenant.verifier/verifier2-service-api",
            urlHost = "openid4vp://authorize",
            key = key,
        )

        assertNotNull(session.setup.core.key) {
            "Signing key must be stored on session.setup.core.key so request_uri POST can re-sign with wallet_nonce"
        }
        assertEquals(RequestUriHttpMethod.POST, session.bootstrapAuthorizationRequest?.requestUriMethod)
    }
}
