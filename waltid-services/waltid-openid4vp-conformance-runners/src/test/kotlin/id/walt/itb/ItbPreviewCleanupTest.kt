package id.walt.itb

import id.walt.wallet2.handlers.PreviewSessionException
import id.walt.wallet2.handlers.PreviewSessionFailureReason
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.*

class ItbPreviewCleanupTest {
    @Test
    fun verifierErrorCategoryRetainsOnlySafeProtocolIdentifiers() {
        assertEquals("missing_sca_credential", ItbWalletDriver.verifierErrorCategory(
            Json.parseToJsonElement("""{"error":"missing_sca_credential","details":"private response"}"""),
        ))
        assertEquals("missing_sca_credential", ItbVerifierRejection(
            Json.parseToJsonElement("""{"error":"missing_sca_credential","details":"private response"}"""),
            HttpStatusCode.BadRequest,
        ).code)
        for (body in listOf(
            """{"error":"token.value.with.dots"}""",
            """{"error":"secret with spaces"}""",
            """{"error":"some_secret"}""",
            """{"error":{"code":"missing_sca_credential"}}""",
            """{"details":"private response"}""",
        )) {
            val response = Json.parseToJsonElement(body)
            assertNull(ItbWalletDriver.verifierErrorCategory(response))
            assertEquals("verifier_http_400", ItbVerifierRejection(response, HttpStatusCode.BadRequest).code)
            assertEquals("verifier_rejected", ItbVerifierRejection(response).code)
            assertFalse(ItbVerifierRejection(response).toString().contains("secret"))
        }
    }

    @Test
    fun successfulSubmissionCanAlreadyHaveConsumedItsPreview() = runBlocking<Unit> {
        var presented = false
        ItbWalletDriver.usePreview(
            discard = { throw PreviewSessionException(PreviewSessionFailureReason.CONSUMED, "consumed") },
            present = { presented = true },
        )
        assertTrue(presented)
    }

    @Test
    fun unexpectedCleanupFailureCannotTurnIntoSuccess() = runBlocking<Unit> {
        val failure = PreviewSessionException(PreviewSessionFailureReason.WRONG_WALLET, "wrong wallet")
        assertSame(failure, assertFailsWith<PreviewSessionException> {
            ItbWalletDriver.usePreview(discard = { throw failure }, present = {})
        })
    }

    @Test
    fun cleanupCannotMaskPresentationFailureOrCancellation() = runBlocking<Unit> {
        for (failure in listOf(IllegalStateException("presentation failed"), CancellationException("cancelled"))) {
            val cleanup = IllegalStateException("cleanup failed")
            val actual = assertFails {
                ItbWalletDriver.usePreview(discard = { throw cleanup }, present = { throw failure })
            }
            assertSame(failure, actual)
            assertEquals(listOf(cleanup), actual.suppressed.toList())
        }
    }
}
