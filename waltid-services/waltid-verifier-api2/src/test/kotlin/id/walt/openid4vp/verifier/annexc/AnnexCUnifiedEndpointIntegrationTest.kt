@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.openid4vp.verifier.annexc

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.cose.Cose
import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.iso18013.annexc.*
import id.walt.iso18013.annexc.cbor.Base64UrlNoPad
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import id.walt.openid4vp.verifier.*
import id.walt.openid4vp.verifier.data.DcApiAnnexCFlowSetup
import id.walt.verifier.openid.models.authorization.ClientMetadata
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.hpke.HPKE
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnnexCUnifiedEndpointIntegrationTest {

    @Serializable
    data class Vector(
        val id: String,
        val origin: String,
        val deviceRequestB64: String,
        val encryptionInfoB64: String,
        val encryptedResponseB64: String,
        val recipientPrivateKeyHex: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun annexCFlowThroughUnifiedEndpoints() {
        val host = "127.0.0.1"
        val port = 17041

        E2ETest(host, port, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service",
                    OSSVerifier2ServiceConfig(
                        clientId = "verifier2",
                        clientMetadata = ClientMetadata(
                            clientName = "Verifier2",
                            logoUri = "https://images.squarespace-cdn.com/content/v1/609c0ddf94bcc0278a7cbdb4/4d493ccf-c893-4882-925f-fda3256c38f4/Walt.id_Logo_transparent.png"
                        ),
                        urlPrefix = "http://$host:$port/verification-session",
                        urlHost = "openid4vp://authorize"
                    )
                )
            },
            init = {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                }
            },
            module = Application::verifierModule
        ) {
            val http = testHttpClient()
            val vector = loadVector()

            val setup: id.walt.openid4vp.verifier.data.VerificationSessionSetup = DcApiAnnexCFlowSetup(
                docType = "org.iso.18013.5.1.mDL",
                requestedElements = mapOf("org.iso.18013.5.1" to listOf("age_over_18")),
                origin = vector.origin,
                ttlSeconds = 300
            )

            val createEnvelope = testAndReturn("Create Annex C session (v2 envelope)") {
                http.post("/verification-session/create?envelope=true") {
                    setBody(setup)
                }.body<UnifiedEnvelope>()
            }

            assertTrue { createEnvelope.flowType == AnnexCService.FLOW_TYPE }
            val sessionId = createEnvelope.sessionId

            val requestEnvelope = testAndReturn("Fetch Annex C request (v2 envelope)") {
                http.get("/verification-session/$sessionId/request?envelope=true").body<UnifiedEnvelope>()
            }

            val requestData = json.decodeFromJsonElement(
                AnnexCService.AnnexCRequestResponse.serializer(),
                requestEnvelope.data
            )
            assertTrue { requestData.protocol == AnnexC.PROTOCOL }

            val deviceResponseBytes = AnnexCResponseVerifierJvm.decryptToDeviceResponse(
                encryptedResponseB64 = vector.encryptedResponseB64,
                encryptionInfoB64 = vector.encryptionInfoB64,
                origin = vector.origin,
                recipientPrivateKey = hexToBytes(vector.recipientPrivateKeyHex)
            )

            val encryptedResponseB64 = buildEncryptedResponse(
                encryptionInfoB64 = requestData.data.encryptionInfo,
                origin = vector.origin,
                deviceResponseBytes = deviceResponseBytes
            )

            testAndReturn("Submit Annex C response (v2 envelope)") {
                http.post("/verification-session/$sessionId/response?envelope=true") {
                    setBody(AnnexCResponsePayload(response = encryptedResponseB64))
                }.body<UnifiedEnvelope>()
            }

            val info = awaitTerminalInfo(http, sessionId)
            assertTrue { info.flowType == AnnexCService.FLOW_TYPE }
            assertNotNull(info.deviceResponseCbor)
            assertTrue {
                info.status == AnnexCService.AnnexCSessionStatus.verified ||
                    info.status == AnnexCService.AnnexCSessionStatus.failed
            }
            if (info.status == AnnexCService.AnnexCSessionStatus.verified) {
                assertNotNull(info.mdocVerificationResult)
            } else {
                assertNotNull(info.error)
            }
        }
    }

    private suspend fun awaitTerminalInfo(
        http: io.ktor.client.HttpClient,
        sessionId: String
    ): AnnexCService.AnnexCInfoResponse {
        repeat(40) {
            val envelope = http.get("/verification-session/$sessionId/info?envelope=true").body<UnifiedEnvelope>()
            val info = json.decodeFromJsonElement(AnnexCService.AnnexCInfoResponse.serializer(), envelope.data)
            if (info.status == AnnexCService.AnnexCSessionStatus.verified ||
                info.status == AnnexCService.AnnexCSessionStatus.failed ||
                info.status == AnnexCService.AnnexCSessionStatus.expired
            ) {
                return info
            }
            delay(250)
        }

        return http.get("/verification-session/$sessionId/info?envelope=true").body<UnifiedEnvelope>().let {
            json.decodeFromJsonElement(AnnexCService.AnnexCInfoResponse.serializer(), it.data)
        }
    }

    private fun buildEncryptedResponse(
        encryptionInfoB64: String,
        origin: String,
        deviceResponseBytes: ByteArray
    ): String {
        val encryptionInfoCbor = Base64UrlNoPad.decode(encryptionInfoB64)
        val encryptionInfo =
            coseCompliantCbor.decodeFromByteArray(DCAPIEncryptionInfo.serializer(), encryptionInfoCbor)

        val hpkeInfo = AnnexCTranscriptBuilder.computeHpkeInfo(encryptionInfoB64, origin)
        val recipientPublicKeyBytes = encryptionInfo.encryptionParameters.recipientPublicKey.toUncompressedP256Point()

        val hpke = HPKE(
            HPKE.mode_base,
            HPKE.kem_P256_SHA256,
            HPKE.kdf_HKDF_SHA256,
            HPKE.aead_AES_GCM128
        )

        val recipientPublicKeyParam = hpke.deserializePublicKey(recipientPublicKeyBytes)
        val sealed = hpke.seal(
            recipientPublicKeyParam,
            hpkeInfo,
            byteArrayOf(),
            deviceResponseBytes,
            null,
            null,
            null
        )

        val encryptedResponseCbor = coseCompliantCbor.encodeToByteArray(
            AnnexCEncryptedResponse.serializer(),
            AnnexCEncryptedResponse(
                type = "dcapi",
                response = AnnexCEncryptedResponseData(enc = sealed[1], cipherText = sealed[0])
            )
        )

        return Base64UrlNoPad.encode(encryptedResponseCbor)
    }

    private fun CoseKey.toUncompressedP256Point(): ByteArray {
        require(kty == Cose.KeyTypes.EC2) { "recipientPublicKey must be EC2" }
        require(crv == Cose.EllipticCurves.P_256) { "recipientPublicKey must be P-256" }
        val xBytes = requireNotNull(x) { "recipientPublicKey.x is missing" }
        val yBytes = requireNotNull(y) { "recipientPublicKey.y is missing" }
        require(xBytes.size == 32) { "recipientPublicKey.x must be 32 bytes for P-256" }
        require(yBytes.size == 32) { "recipientPublicKey.y must be 32 bytes for P-256" }
        return byteArrayOf(0x04) + xBytes + yBytes
    }

    private fun loadVector(): Vector {
        val resource = javaClass.getResource("/annex-c/ANNEXC-DETERMINISTIC-001.json")
            ?: error("Test vector resource annex-c/ANNEXC-DETERMINISTIC-001.json not found on classpath")
        val text = File(resource.toURI()).readText()
        return json.decodeFromString(Vector.serializer(), text)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().removePrefix("0x").replace(" ", "").replace("\n", "")
        require(clean.length % 2 == 0) { "hex length must be even" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

}
