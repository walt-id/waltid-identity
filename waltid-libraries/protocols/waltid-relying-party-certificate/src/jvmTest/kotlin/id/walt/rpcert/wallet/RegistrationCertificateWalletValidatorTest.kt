package id.walt.rpcert.wallet

import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.dcql.models.meta.NoMeta
import id.walt.rpcert.models.RegistrationCertificateCredential
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class RegistrationCertificateWalletValidatorTest {

    private val coveredDcqlQuery = DcqlQuery(
        credentials = listOf(CredentialQuery(id = "q1", format = CredentialFormat.DC_SD_JWT, meta = NoMeta)),
    )

    @Test
    fun validateReturnsAllowedWhenCertificateCoversDcqlQuery() = runTest {
        val credential = RegistrationCertificateCredential(format = CredentialFormat.DC_SD_JWT, meta = NoMeta)
        val jwt = RpCertTestFixtures.signedCertificateJwt(payload = RpCertTestFixtures.sampleCertificate(credentials = listOf(credential)))

        val result = RegistrationCertificateWalletValidator.validate(coveredDcqlQuery, jwt, trustAnchors = selfTrustAnchor(jwt))

        assertIs<RegistrationValidationResult.Allowed>(result)
        assertTrue(result.allowed)
    }

    @Test
    fun validateReturnsRequestNotCoveredWhenQueryExceedsRegistration() = runTest {
        val credential = RegistrationCertificateCredential(format = CredentialFormat.MSO_MDOC, meta = MsoMdocMeta("org.iso.18013.5.1.mDL"))
        val jwt = RpCertTestFixtures.signedCertificateJwt(payload = RpCertTestFixtures.sampleCertificate(credentials = listOf(credential)))

        val result = RegistrationCertificateWalletValidator.validate(coveredDcqlQuery, jwt, trustAnchors = selfTrustAnchor(jwt))

        assertIs<RegistrationValidationResult.RequestNotCovered>(result)
        assertFalse(result.allowed)
    }

    @Test
    fun validateReturnsInvalidRegistrationCertificateWhenJwtIsBad() = runTest {
        val result = RegistrationCertificateWalletValidator.validate(coveredDcqlQuery, "not-a-jwt", allowTrustedChainRoot = true)

        assertIs<RegistrationValidationResult.InvalidRegistrationCertificate>(result)
        assertFalse(result.allowed)
    }

    @Test
    fun validateReturnsMissingDcqlQueryWhenAuthorizationRequestHasNone() = runTest {
        val jwt = RpCertTestFixtures.signedCertificateJwt()
        val authorizationRequest = AuthorizationRequest(dcqlQuery = null)

        val result = RegistrationCertificateWalletValidator.validate(authorizationRequest, jwt, allowTrustedChainRoot = true)

        assertIs<RegistrationValidationResult.MissingDcqlQuery>(result)
        assertFalse(result.allowed)
    }

    @Test
    fun validateWithAuthorizationRequestDelegatesToDcqlQueryOverload() = runTest {
        val credential = RegistrationCertificateCredential(format = CredentialFormat.DC_SD_JWT, meta = NoMeta)
        val jwt = RpCertTestFixtures.signedCertificateJwt(payload = RpCertTestFixtures.sampleCertificate(credentials = listOf(credential)))
        val authorizationRequest = AuthorizationRequest(dcqlQuery = coveredDcqlQuery)

        val result = RegistrationCertificateWalletValidator.validate(authorizationRequest, jwt, trustAnchors = selfTrustAnchor(jwt))

        assertIs<RegistrationValidationResult.Allowed>(result)
    }

    // The x5c chain is a single self-signed leaf with no separate root cert, so it must be
    // trusted explicitly (allowTrustedChainRoot alone only covers a root distinct from the leaf).
    private fun selfTrustAnchor(jwt: String) = RelyingPartyRegistrationCertificateVerifier.decode(jwt).certificateChain
}
