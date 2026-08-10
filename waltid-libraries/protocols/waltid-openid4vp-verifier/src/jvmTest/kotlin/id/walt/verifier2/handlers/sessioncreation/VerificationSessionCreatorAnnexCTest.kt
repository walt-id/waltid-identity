@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.sessioncreation

import id.walt.cose.toCoseVerifier
import id.walt.iso18013.annexc.AnnexCTranscriptBuilder
import id.walt.iso18013.annexc.protocol.AnnexCRequestResponse
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.ReaderAuthenticationPayloads
import id.walt.verifier2.data.DcApiAnnexCFlowSetup
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class VerificationSessionCreatorAnnexCTest {

    @Test
    fun `signed Annex C request authenticates exact tagged request bytes`() = runTest {
        val setup = DcApiAnnexCFlowSetup.SIGNED_MDL_EXAMPLE
        val signingKey = requireNotNull(setup.core.key)
        val session = VerificationSessionCreator.createVerificationSession(
            setup = setup,
            clientId = setup.core.clientId,
            urlPrefix = null,
            urlHost = setup.origin,
            key = signingKey.key,
            x5c = setup.core.x5c,
        )
        val response = Json.decodeFromJsonElement(
            AnnexCRequestResponse.serializer(),
            requireNotNull(session.data),
        )
        val request = DeviceRequest.decodeFromBase64Url(response.data.deviceRequest)
        val transcript = AnnexCTranscriptBuilder.buildSessionTranscript(
            encryptionInfoB64 = response.data.encryptionInfo,
            origin = setup.origin,
        )
        val verifier = signingKey.key.getPublicKey().toCoseVerifier()

        request.docRequests.forEach { documentRequest ->
            assertTrue(
                requireNotNull(documentRequest.readerAuth).verifyDetached(
                    verifier,
                    ReaderAuthenticationPayloads.forDocument(transcript, documentRequest.itemsRequest),
                )
            )
        }
        request.readerAuthAll.orEmpty().forEach { readerAuthAll ->
            assertTrue(
                readerAuthAll.verifyDetached(
                    verifier,
                    ReaderAuthenticationPayloads.forAllDocuments(
                        sessionTranscript = transcript,
                        itemsRequests = request.docRequests.map { it.itemsRequest },
                        deviceRequestInfo = request.deviceRequestInfo,
                    ),
                )
            )
        }
    }
}
