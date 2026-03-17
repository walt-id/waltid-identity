@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.tests

import io.klogging.Klogging
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi

@TestMethodOrder(OrderAnnotation::class)
class TrustManagementIntegrationTest : AbstractIntegrationTest(), Klogging {

    @Order(0)
    @Test
    fun shouldValidateIssuerTrust() = runTest {
        val result = defaultWalletApi.validateIssuerTrust(
            did = "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
            credentialType = "VerifiableCredential",
            egfUri = "https://example.com/egf"
        )
        assertNotNull(result)
        logger.info("Issuer trust validation result: ${result.trusted}")
    }

    @Order(1)
    @Test
    fun shouldValidateVerifierTrust() = runTest {
        val result = defaultWalletApi.validateVerifierTrust(
            did = "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
            credentialType = "VerifiableCredential",
            egfUri = "https://example.com/egf"
        )
        assertNotNull(result)
        logger.info("Verifier trust validation result: ${result.trusted}")
    }

    @Order(2)
    @Test
    fun shouldHandleUnknownDid() = runTest {
        val result = defaultWalletApi.validateIssuerTrust(
            did = "did:key:unknown",
            credentialType = "VerifiableCredential",
            egfUri = "https://example.com/egf"
        )
        assertNotNull(result)
        logger.info("Unknown DID trust validation result: ${result.trusted}")
    }

    @Order(3)
    @Test
    fun shouldHandleDifferentCredentialTypes() = runTest {
        val types = listOf(
            "VerifiableCredential",
            "OpenBadgeCredential",
            "VerifiableAttestation"
        )
        
        for (type in types) {
            val result = defaultWalletApi.validateIssuerTrust(
                did = "did:key:z6MkjoRhq1jSNJdLiruSXrFFxagqrztZaXHqHGUTKJbcNywp",
                credentialType = type,
                egfUri = "https://example.com/egf"
            )
            assertNotNull(result)
            logger.info("Trust validation for $type: ${result.trusted}")
        }
    }
}
