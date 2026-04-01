package id.walt.openid4vci.metadata.issuer

import kotlin.test.Test
import kotlin.test.assertFailsWith

class BatchCredentialIssuanceTest {

    @Test
    fun `batch size must be at least two`() {
        assertFailsWith<IllegalArgumentException> {
            BatchCredentialIssuance(batchSize = 1)
        }
        BatchCredentialIssuance(batchSize = 2)
    }
}
