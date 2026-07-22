@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.vpresponse

import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.hpke.Hpke
import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.DcApiAnnexCFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreator
import id.walt.iso18013.annexc.AnnexCEncryptedResponse
import id.walt.iso18013.annexc.AnnexCEncryptedResponseData
import id.walt.iso18013.annexc.AnnexCTranscriptBuilder
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.DcApiJsonDirectPostResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class Verifier2Crypto2EncryptedResponseTest {
    private val crypto2Runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `encrypted response key survives serialized session restart`() = runTest {
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery(
                                id = "pid",
                                format = CredentialFormat.DC_SD_JWT,
                                meta = NoMeta,
                            )
                        )
                    ),
                    encryptedResponse = true,
                )
            ),
            clientId = "verifier",
            urlPrefix = "https://verifier.example/session",
            urlHost = "openid4vp://authorize",
        )
        assertNotNull(session.ephemeralDecryptionKey)
        assertNotNull(session.crypto2EphemeralDecryptionKey)
        val publicJwk = assertNotNull(session.authorizationRequest.clientMetadata?.jwks).keys.single()
        val plaintext = buildJsonObject {
            put("vp_token", buildJsonObject { })
            put("state", session.authorizationRequest.state)
        }.toString().encodeToByteArray()
        val encrypted = CompactJwe.encrypt(
            plaintext = plaintext,
            recipientPublicKey = EncodedKey.Jwk(
                data = BinaryData(Json.encodeToString(publicJwk).encodeToByteArray()),
                privateMaterial = false,
            ),
            contentEncryption = JweContentEncryption.A256GCM,
            protectedHeader = buildJsonObject { put("kid", requireNotNull(publicJwk["kid"])) },
        )

        val persistedKey = assertNotNull(session.crypto2EphemeralDecryptionKey)
        val serializedSession = Json.encodeToString(session)
        val restoredSession = Json.decodeFromString<Verification2Session>(serializedSession)
        assertNull(restoredSession.crypto2EphemeralDecryptionKey)
        assertEquals(false, serializedSession.contains("crypto2EphemeralDecryptionKey"))
        val decrypted = Verifier2VPDirectPostHandler.decryptDirectPostJwe(
            compactJwe = encrypted,
            legacyKey = restoredSession.ephemeralDecryptionKey,
            crypto2StoredKey = persistedKey,
        )

        assertEquals(plaintext.decodeToString(), decrypted.decodeToString())
        val legacyDecrypted = Verifier2VPDirectPostHandler.decryptDirectPostJwe(
            compactJwe = encrypted,
            legacyKey = assertNotNull(session.ephemeralDecryptionKey),
            crypto2StoredKey = null,
        )
        assertEquals(plaintext.decodeToString(), legacyDecrypted.decodeToString())

        val missingKid = CompactJwe.encrypt(
            plaintext = plaintext,
            recipientPublicKey = EncodedKey.Jwk(
                BinaryData(Json.encodeToString(publicJwk).encodeToByteArray()),
                privateMaterial = false,
            ),
            contentEncryption = JweContentEncryption.A128GCM,
        )
        assertFailsWith<IllegalArgumentException> {
            Verifier2VPDirectPostHandler.decryptDirectPostJwe(missingKid, null, persistedKey)
        }
        val wrongKid = CompactJwe.encrypt(
            plaintext = plaintext,
            recipientPublicKey = EncodedKey.Jwk(
                BinaryData(Json.encodeToString(publicJwk).encodeToByteArray()),
                privateMaterial = false,
            ),
            contentEncryption = JweContentEncryption.A128GCM,
            protectedHeader = buildJsonObject { put("kid", "wrong") },
        )
        assertFailsWith<IllegalArgumentException> {
            Verifier2VPDirectPostHandler.decryptDirectPostJwe(wrongKid, null, persistedKey)
        }
        assertFailsWith<IllegalArgumentException> {
            Verifier2VPDirectPostHandler.decryptDirectPostJwe(
                replaceContentEncryption(encrypted, "A192GCM"),
                null,
                persistedKey,
            )
        }
    }

    @Test
    fun `Annex C HPKE key survives persisted session restart with legacy fallback`() = runTest {
        val origin = "https://verifier.example"
        val session = VerificationSessionCreator.createVerificationSession(
            setup = DcApiAnnexCFlowSetup(
                requestedElements = mapOf(
                    "org.iso.18013.5.1.mDL" to mapOf(
                        "org.iso.18013.5.1" to listOf("family_name")
                    )
                ),
                origin = origin,
            ),
            clientId = null,
            urlPrefix = null,
            urlHost = origin,
        )
        val persistedKey = assertNotNull(session.crypto2EphemeralDecryptionKey)
        val restoredKey = crypto2Runtime.restore(StoredKeyCodec.decodeFromString(persistedKey))
        assertEquals(KeySpec.Ec(EcCurve.P256), restoredKey.spec)
        assertEquals(setOf(KeyUsage.KEY_AGREEMENT), restoredKey.usages)
        assertNotNull(session.ephemeralDecryptionKey)
        val publicJwk = restoredKey.capabilities.publicKeyExporter?.exportPublicKey() as EncodedKey.Jwk
        val encryptionInfoB64 = session.data?.jsonObject
            ?.get("data")?.jsonObject
            ?.get("encryptionInfo")?.jsonPrimitive?.content
            ?: error("Missing Annex C encryption info")
        val encryptionInfo = coseCompliantCbor.decodeFromByteArray(
            DCAPIEncryptionInfo.serializer(),
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(encryptionInfoB64),
        )
        assertEquals(publicJwk.toCoseKey(), encryptionInfo.encryptionParameters.recipientPublicKey)

        val plaintext = "persisted-annex-c-device-response".encodeToByteArray()
        val sealed = Hpke.sealBase(
            recipientPublicKey = publicJwk,
            plaintext = plaintext,
            info = AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, origin),
        )
        val encryptedResponseB64 = coseCompliantCbor.encodeToByteArray(
            AnnexCEncryptedResponse.serializer(),
            AnnexCEncryptedResponse(
                type = "dcapi",
                response = AnnexCEncryptedResponseData(
                    enc = sealed.encapsulatedKey.toByteArray(),
                    cipherText = sealed.ciphertext.toByteArray(),
                ),
            ),
        ).encodeToBase64Url()
        val serializedSession = Json.encodeToString(session)
        val restoredSession = Json.decodeFromString<Verification2Session>(serializedSession)
        assertNull(restoredSession.crypto2EphemeralDecryptionKey)
        assertTrue(!serializedSession.contains("crypto2EphemeralDecryptionKey"))

        assertContentEquals(
            plaintext,
            Verifier2VPDirectPostHandler.decryptAnnexCResponse(
                encryptedResponseB64,
                encryptionInfoB64,
                origin,
                restoredSession.ephemeralDecryptionKey,
                persistedKey,
            ),
        )
        assertContentEquals(
            plaintext,
            Verifier2VPDirectPostHandler.decryptAnnexCResponse(
                encryptedResponseB64,
                encryptionInfoB64,
                origin,
                assertNotNull(session.ephemeralDecryptionKey),
                null,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            Verifier2VPDirectPostHandler.decryptAnnexCResponse(
                encryptedResponseB64,
                encryptionInfoB64,
                origin,
                assertNotNull(session.ephemeralDecryptionKey),
                "not-a-stored-key",
            )
        }

        suspend fun encryptedDeviceResponse(version: String, status: UInt): String {
            val encoded = coseCompliantCbor.encodeToByteArray(
                DeviceResponse.serializer(),
                DeviceResponse(version = version, documents = emptyArray(), status = status),
            )
            val encrypted = Hpke.sealBase(
                publicJwk,
                encoded,
                AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, origin),
            )
            return coseCompliantCbor.encodeToByteArray(
                AnnexCEncryptedResponse.serializer(),
                AnnexCEncryptedResponse(
                    type = "dcapi",
                    response = AnnexCEncryptedResponseData(
                        enc = encrypted.encapsulatedKey.toByteArray(),
                        cipherText = encrypted.ciphertext.toByteArray(),
                    ),
                ),
            ).encodeToBase64Url()
        }
        assertFailsWith<IllegalArgumentException> {
            Verifier2VPDirectPostHandler.parseResponseBody(
                session.authorizationRequest.responseMode,
                DcApiJsonDirectPostResponse(
                    buildJsonObject { put("response", encryptedDeviceResponse("1.0", 1u)) }
                ),
                session,
                session.ephemeralDecryptionKey,
                persistedKey,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            Verifier2VPDirectPostHandler.parseResponseBody(
                session.authorizationRequest.responseMode,
                DcApiJsonDirectPostResponse(
                    buildJsonObject { put("response", encryptedDeviceResponse("2.0", 0u)) }
                ),
                session,
                session.ephemeralDecryptionKey,
                persistedKey,
            )
        }
    }

    private fun replaceContentEncryption(jwe: String, encryption: String): String {
        val parts = jwe.split('.').toMutableList()
        val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = Json.parseToJsonElement(base64Url.decode(parts[0]).decodeToString()).jsonObject
        parts[0] = base64Url.encode(
            Json.encodeToString(JsonObject(header + ("enc" to JsonPrimitive(encryption)))).encodeToByteArray()
        )
        return parts.joinToString(".")
    }
}
