package id.walt.wallet2.handlers

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReleasedOfferResultTest {
    @Test
    fun batchCapabilityRemainsInResolvedMetadataWithoutChangingTheReleasedSummary() = runTest {
        for (batch in listOf(false, true)) {
            val metadata = batchTestMetadata(2).let {
                if (batch) it else it.replace("\"batch_credential_issuance\":{\"batch_size\":2},", "")
            }
            val http = batchTestClient(metadata = metadata,
                token = { error("Offer resolution must not redeem a grant") },
                credential = { error("Offer resolution must not issue credentials") })
            try {
                val resolved = WalletIssuanceHandler.resolveOfferDetailed(ResolveOfferRequest(offerJson = batchTestOffer()), http)
                assertEquals(if (batch) 2 else null, resolved.resolvedIssuerMetadata.metadata.batchCredentialIssuance?.batchSize)
                val json = Json { encodeDefaults = true }
                val encoded = json.encodeToString(resolved.summary)
                assertFalse("batchSize" in Json.parseToJsonElement(encoded).jsonObject)
                assertEquals(resolved.summary, json.decodeFromString<ResolveOfferResult>(encoded))
                assertEquals(listOf("identity"), resolved.summary.credentialConfigurationIds)
            } finally {
                http.close()
            }
        }
    }
}
