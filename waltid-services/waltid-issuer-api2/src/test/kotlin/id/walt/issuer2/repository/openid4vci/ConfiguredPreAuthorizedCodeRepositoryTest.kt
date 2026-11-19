package id.walt.issuer2.repository.openid4vci

import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.offers.TxCode
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.repository.preauthorized.DefaultPreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRedemptionState
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ConfiguredPreAuthorizedCodeRepositoryTest {

    @Test
    fun saveGetRejectDuplicateAndRedemptionIsOneTime() = runTest {
        val repository = ConfiguredPreAuthorizedCodeRepository()
        val record = testRecord()

        repository.save(record)

        assertEquals(record, repository.get(record.code))
        assertFailsWith<DuplicateCodeException> {
            repository.save(record)
        }

        assertTrue(repository.compareAndSetRedemptionState(record, redeemed))
        assertNull(repository.get(record.code))
        assertFalse(repository.compareAndSetRedemptionState(record, redeemed))
    }

    @Test
    fun concurrentClaimHasOneWinner() = runTest {
        val repository = ConfiguredPreAuthorizedCodeRepository()
        val record = testRecord()

        repository.save(record)

        val results = (1..20)
            .map { async(Dispatchers.Default) { repository.compareAndSetRedemptionState(record, redeemed) } }
            .awaitAll()

        assertEquals(1, results.count { it })
    }

    private val redeemed = PreAuthorizedCodeRedemptionState(0, PreAuthorizedCodeStatus.REDEEMED)

    private fun testRecord() = DefaultPreAuthorizedCodeRecord(
        code = "pre-auth-code-${Clock.System.now().toEpochMilliseconds()}",
        clientId = null,
        txCode = TxCode(inputMode = "numeric", length = 6, description = "PIN"),
        txCodeValue = "hashed-pin",
        grantedScopes = setOf("openid"),
        grantedAudience = setOf("issuer2"),
        session = DefaultSession(subject = "subject"),
        expiresAt = Clock.System.now().plus(5.minutes),
        issuanceSessionId = "issuance-session-id",
    )
}
