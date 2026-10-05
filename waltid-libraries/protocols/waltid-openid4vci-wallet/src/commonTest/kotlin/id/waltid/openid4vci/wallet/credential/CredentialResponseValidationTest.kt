package id.waltid.openid4vci.wallet.credential

import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CredentialResponseValidationTest {
    private val issued = CredentialResponse(credentials = listOf(IssuedCredential(JsonPrimitive("credential"))))
    private val pending = CredentialResponse(transactionId = "transaction", interval = 5)

    @Test fun issuedAndPendingResponsesHaveDistinctHttpContracts() {
        assertEquals(issued, issued.validateCredentialResponse(200))
        assertEquals(pending, pending.validateCredentialResponse(202, "transaction"))
        assertFailsWith<IllegalArgumentException> { issued.validateCredentialResponse(202) }
        assertFailsWith<IllegalArgumentException> { pending.validateCredentialResponse(200) }
        assertFailsWith<IllegalStateException> { issued.validateCredentialResponse(201) }
    }

    @Test fun contradictoryOrEmptyIssuedResponsesAreRejected() {
        for (invalid in listOf(
            CredentialResponse(), issued.copy(credentials = emptyList()),
            issued.copy(transactionId = "transaction"), issued.copy(interval = 5),
        )) assertFailsWith<IllegalArgumentException> { invalid.validateCredentialResponse(200) }
    }

    @Test fun deferredResponsesRequirePositiveIntervalAndTheOriginalTransaction() {
        for (invalid in listOf(
            pending.copy(transactionId = ""), pending.copy(interval = null),
            pending.copy(interval = 0), pending.copy(interval = -1),
            pending.copy(credentials = issued.credentials), pending.copy(notificationId = "notification"),
        )) assertFailsWith<IllegalArgumentException> { invalid.validateCredentialResponse(202) }
        assertFailsWith<IllegalArgumentException> { pending.validateCredentialResponse(202, "different") }
        assertEquals(7, pending.copy(interval = 7).validateCredentialResponse(202, "transaction").interval)
    }
}
