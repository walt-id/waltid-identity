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
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.handlers.authrequest.Verifier2RequestObjectKid
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
}
