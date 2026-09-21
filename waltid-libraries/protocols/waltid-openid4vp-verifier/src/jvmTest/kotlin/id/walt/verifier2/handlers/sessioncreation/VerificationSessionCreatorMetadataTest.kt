@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.sessioncreation

import id.walt.cose.Cose
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.did.dids.DidService
import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.handlers.authrequest.Verifier2RequestObjectKid
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VerificationSessionCreatorMetadataTest {

    @Test
    fun `signed cross-device session exposes an inline authenticated request object`() = runTest {
        DidService.minimalInit()
        val verifierKey = JWKKey.generate(KeyType.secp256r1)
        val did = DidService.registerByKey("jwk", verifierKey).did
        val clientId = "decentralized_identifier:$did"
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    signedRequest = true,
                    clientId = clientId,
                    key = DirectSerializedKey(verifierKey),
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = NoMeta)
                        )
                    )
                )
            ),
            clientId = clientId,
            urlPrefix = "https://verifier.example.com/verification-session",
            urlHost = "openid4vp://authorize",
            key = verifierKey,
        )

        val fullUrl = assertNotNull(session.toSessionCreationResponse().fullAuthorizationRequestUrl)
        val requestObject = assertNotNull(fullUrl.parameters["request"])
        assertNull(fullUrl.parameters["request_uri"])
        assertEquals(clientId, fullUrl.parameters["client_id"])

        val jwtParts = requestObject.split('.')
        val header = Json.parseToJsonElement(
            java.util.Base64.getUrlDecoder().decode(jwtParts[0]).decodeToString()
        ).jsonObject
        val payload = Json.parseToJsonElement(
            java.util.Base64.getUrlDecoder().decode(jwtParts[1]).decodeToString()
        ).jsonObject
        val kid = assertNotNull(header["kid"]?.jsonPrimitive?.content)
        assertEquals(Verifier2RequestObjectKid.forClient(clientId, verifierKey), kid)
        verifierKey.getPublicKey().verifyJws(requestObject).getOrThrow()

        assertEquals("oauth-authz-req+jwt", header["typ"]?.jsonPrimitive?.content)
        assertEquals("https://self-issued.me/v2", payload["aud"]?.jsonPrimitive?.content)
        assertEquals(clientId, payload["client_id"]?.jsonPrimitive?.content)
        assertEquals(clientId, fullUrl.parameters["client_id"])
    }

    @Test
    fun `default mdoc metadata advertises EdDSA device authentication support`() = runTest {
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery(
                                id = "mdl",
                                format = CredentialFormat.MSO_MDOC,
                                meta = NoMeta,
                            )
                        )
                    )
                )
            ),
            clientId = "verifier",
            urlPrefix = "https://verifier.example.com/verification-session",
            urlHost = "openid4vp://authorize",
        )

        val mdocMetadata = assertNotNull(
            session.authorizationRequest.clientMetadata?.vpFormatsSupported?.get("mso_mdoc")
        )
        val deviceAuthAlgorithms = mdocMetadata.getValue("deviceauth_alg_values")
            .jsonArray
            .map { it.jsonPrimitive.content.toInt() }

        assertContains(deviceAuthAlgorithms, Cose.Algorithm.EdDSA)
    }

    @Test
    fun `pre-registered unsigned request omits in-band client_metadata`() = runTest {
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = NoMeta)
                        )
                    )
                )
            ),
            clientId = "verifier2",
            clientMetadata = ClientMetadata(clientName = "Registered Verifier"),
            urlPrefix = "https://verifier.example.com/verification-session",
            urlHost = "openid4vp://authorize",
        )

        assertNotNull(session.authorizationRequest.clientMetadata?.vpFormatsSupported)
        val url = assertNotNull(session.authorizationRequestUrl)
        assertNull(url.parameters["client_metadata"])
        assertEquals("verifier2", session.authorizationRequest.clientId)
    }

    @Test
    fun `pre-registered signed request omits in-band client_metadata from the jwt`() = runTest {
        DidService.minimalInit()
        val verifierKey = JWKKey.generate(KeyType.secp256r1)
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    signedRequest = true,
                    clientId = "verifier2",
                    key = DirectSerializedKey(verifierKey),
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = NoMeta)
                        )
                    )
                )
            ),
            clientId = "verifier2",
            clientMetadata = ClientMetadata(clientName = "Registered Verifier"),
            urlPrefix = "https://verifier.example.com/verification-session",
            urlHost = "openid4vp://authorize",
            key = verifierKey,
        )

        val requestObject = assertNotNull(session.signedAuthorizationRequestJwt)
        val payload = Json.parseToJsonElement(
            java.util.Base64.getUrlDecoder().decode(requestObject.split('.')[1]).decodeToString()
        ).jsonObject
        assertFalse(payload.containsKey("client_metadata"))
        assertEquals("verifier2", payload["client_id"]?.jsonPrimitive?.content)
        assertNotNull(session.authorizationRequest.clientMetadata?.vpFormatsSupported)
    }

    @Test
    fun `pre-registered encrypted session decrypts with the registered encryption key`() = runTest {
        DidService.minimalInit()
        val verifierKey = JWKKey.generate(KeyType.secp256r1)
        val encKey = JWKKey.generate(KeyType.secp256r1)
        val privateEncJwk = JsonObject(
            encKey.exportJWKObject().toMutableMap().apply {
                put("alg", JsonPrimitive("ECDH-ES"))
                put("use", JsonPrimitive("enc"))
                put("kid", JsonPrimitive(encKey.getKeyId()))
            }
        )
        val publicEncJwk = JsonObject(privateEncJwk.filterKeys { it != "d" })
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    signedRequest = true,
                    encryptedResponse = true,
                    clientId = "verifier2",
                    key = DirectSerializedKey(verifierKey),
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = NoMeta)
                        )
                    )
                )
            ),
            clientId = "verifier2",
            clientMetadata = ClientMetadata(
                jwks = ClientMetadata.Jwks(
                    listOf(verifierKey.getPublicKey().exportJWKObject(), privateEncJwk),
                ),
            ),
            urlPrefix = "https://verifier.example.com/verification-session",
            urlHost = "openid4vp://authorize",
            key = verifierKey,
        )

        val requestObject = assertNotNull(session.signedAuthorizationRequestJwt)
        val payload = Json.parseToJsonElement(
            java.util.Base64.getUrlDecoder().decode(requestObject.split('.')[1]).decodeToString()
        ).jsonObject
        assertFalse(payload.containsKey("client_metadata"))
        assertNotNull(session.ephemeralDecryptionKey)
        assertEquals(
            encKey.getKeyId(),
            session.authorizationRequest.clientMetadata?.jwks?.keys?.single { it["use"]?.jsonPrimitive?.content == "enc" }
                ?.get("kid")?.jsonPrimitive?.content,
        )

        val plaintext = buildJsonObject { put("vp_token", buildJsonObject { }) }.toString().encodeToByteArray()
        val encrypted = CompactJwe.encrypt(
            plaintext = plaintext,
            recipientPublicKey = EncodedKey.Jwk(
                data = BinaryData(Json.encodeToString(publicEncJwk).encodeToByteArray()),
                privateMaterial = false,
            ),
            contentEncryption = JweContentEncryption.A256GCM,
            protectedHeader = buildJsonObject { put("kid", JsonPrimitive(encKey.getKeyId())) },
        )
        val decrypted = Verifier2VPDirectPostHandler.decryptDirectPostJwe(
            encrypted,
            session.ephemeralDecryptionKey,
            session.crypto2EphemeralDecryptionKey,
        )
        assertEquals(plaintext.decodeToString(), decrypted.decodeToString())
    }
}
