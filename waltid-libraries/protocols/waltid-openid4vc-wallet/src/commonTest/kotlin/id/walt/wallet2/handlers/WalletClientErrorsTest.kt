package id.walt.wallet2.handlers

import id.walt.errors.ClientFacingError
import id.walt.errors.HttpStatusError
import id.walt.errors.ClientRequestException
import id.walt.wallet2.consent.PaymentConsentException
import id.walt.wallet2.consent.PaymentConsentFailure
import id.walt.wallet2.data.HolderKeyBindingErrorCode
import id.walt.wallet2.data.HolderKeyBindingException
import id.walt.wallet2.data.Wallet
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * A wallet failure the caller can act on - a missing or conflicting parameter, a key the wallet does not hold,
 * a preview or credential that changed - must carry a 4xx status so the service returns its wallet-written
 * message, instead of an opaque internal error. Corrupt stored data and outages stay server errors.
 */
class WalletClientErrorsTest {

    @Test
    fun `receiving without any usable signing key is a client error that names the fix`() = runTest {
        val failure = assertFailsWith<WalletSigningKeyUnavailableException> {
            WalletIssuanceHandler.receiveCredential(
                Wallet(id = "wallet-without-keys"),
                ReceiveCredentialRequest(offerJson = batchTestOffer()),
                httpClient = HttpClient(),
            )
        }

        assertEquals(400, failure.status)
        assertContains(failure.message.orEmpty(), "neither an inline key nor a keyId was supplied")
    }

    @Test
    fun `naming a key the wallet does not hold is a client error`() = runTest {
        val failure = assertFailsWith<ClientRequestException> {
            WalletIssuanceHandler.receiveCredential(
                Wallet(id = "wallet-without-keys"),
                ReceiveCredentialRequest(offerJson = batchTestOffer(), keyId = "missing-key"),
                httpClient = HttpClient(),
            )
        }

        assertEquals(400, failure.status)
        assertContains(failure.message.orEmpty(), "missing-key")
    }

    @Test
    fun `an offer given both as URL and JSON is a client error`() {
        val failure = assertFailsWith<ClientRequestException> {
            ReceiveCredentialRequest(offerUrl = Url("openid-credential-offer://?credential_offer_uri=x"), offerJson = batchTestOffer())
        }

        assertEquals(400, failure.status)
        assertEquals("Only one of offerUrl or offerJson may be provided, not both", failure.message)
    }

    @Test
    fun `holder-key binding failures are conflicts except corrupt data and outages`() {
        HolderKeyBindingErrorCode.entries.forEach { code ->
            val expected = when (code) {
                HolderKeyBindingErrorCode.CREDENTIAL_NOT_FOUND -> 404
                HolderKeyBindingErrorCode.BINDING_INVALID -> 500
                HolderKeyBindingErrorCode.KEY_PROVIDER_UNAVAILABLE -> 503
                else -> 409
            }
            assertEquals(expected, HolderKeyBindingException(code, "credential", "message").status, code.name)
        }
    }

    @Test
    fun `payment consent failures carry the status of who must act`() {
        PaymentConsentFailure.entries.forEach { reason ->
            val expected = when (reason) {
                PaymentConsentFailure.CONSENT_REQUIRED, PaymentConsentFailure.STALE_CONSENT -> 409
                PaymentConsentFailure.METADATA_UNAVAILABLE -> 502
                else -> 422
            }
            assertEquals(expected, PaymentConsentException(reason).status, reason.name)
        }
    }

    @Test
    fun `preview session failures tell the caller to preview again`() {
        PreviewSessionFailureReason.entries.forEach { reason ->
            val expected = when (reason) {
                PreviewSessionFailureReason.CONSUMED, PreviewSessionFailureReason.IN_USE -> 409
                PreviewSessionFailureReason.CAPACITY_EXCEEDED -> 503
                else -> 404
            }
            assertEquals(expected, PreviewSessionException(reason, "message").status, reason.name)
        }
        assertEquals(404, MissingPresentationPreviewException().status)
    }

    @Test
    fun `caller-fixable wallet errors are client-facing with their status`() {
        listOf<Throwable>(
            WalletSigningKeyUnavailableException("x"),
            HolderKeyBindingException(HolderKeyBindingErrorCode.KEY_NOT_FOUND, "credential", "x"),
            PaymentConsentException(PaymentConsentFailure.CONSENT_REQUIRED),
            PreviewSessionException(PreviewSessionFailureReason.EXPIRED, "x"),
            MissingPresentationPreviewException(),
            WalletInstanceAttestationRequiredException(),
        ).forEach {
            assertIs<ClientFacingError>(it)
            assertIs<HttpStatusError>(it)
        }
        // A request the caller can fix is an invalid argument, which the mobile bridge reports as invalid input.
        assertIs<IllegalArgumentException>(WalletSigningKeyUnavailableException("x"))
    }
}
