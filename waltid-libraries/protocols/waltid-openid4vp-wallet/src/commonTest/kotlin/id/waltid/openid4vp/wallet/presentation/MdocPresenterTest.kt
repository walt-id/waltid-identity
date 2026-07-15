@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.presentation

import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.mdoc.objects.handover.OpenID4VPHandoverInfo
import id.walt.mdoc.objects.sha256
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class MdocPresenterTest {

    private val authorizationRequest = AuthorizationRequest(
        clientId = "x509_hash:verifier-certificate-hash",
        nonce = "presentation-nonce",
        responseUri = "https://verifier.example/response",
    )

    @Test
    fun `unencrypted transcript hashes handover info with null key thumbprint`() {
        val transcript = MdocPresenter.buildSessionTranscript(
            authorizationRequest,
            authorizationRequest.responseUri!!,
            encryptionKeyThumbprint = null,
        )
        val expectedInfo = OpenID4VPHandoverInfo(
            clientId = authorizationRequest.clientId!!,
            nonce = authorizationRequest.nonce!!,
            jwkThumbprint = null,
            responseUri = authorizationRequest.responseUri,
        )

        assertContentEquals(
            coseCompliantCbor.encodeToByteArray(expectedInfo).sha256(),
            transcript.oid4VPHandover!!.infoHash,
        )
    }

    @Test
    fun `encrypted transcript binds exact response encryption key thumbprint`() {
        val thumbprint = "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30"
        val encryptedTranscript = MdocPresenter.buildSessionTranscript(
            authorizationRequest,
            authorizationRequest.responseUri!!,
            encryptionKeyThumbprint = thumbprint,
        )
        val unencryptedTranscript = MdocPresenter.buildSessionTranscript(
            authorizationRequest,
            authorizationRequest.responseUri!!,
            encryptionKeyThumbprint = null,
        )
        val expectedInfo = OpenID4VPHandoverInfo(
            clientId = authorizationRequest.clientId!!,
            nonce = authorizationRequest.nonce!!,
            jwkThumbprint = thumbprint.decodeFromBase64Url(),
            responseUri = authorizationRequest.responseUri,
        )

        assertContentEquals(
            coseCompliantCbor.encodeToByteArray(expectedInfo).sha256(),
            encryptedTranscript.oid4VPHandover!!.infoHash,
        )
        assertFalse(
            encryptedTranscript.oid4VPHandover!!.infoHash.contentEquals(
                unencryptedTranscript.oid4VPHandover!!.infoHash
            )
        )
    }
}
