package id.walt.rpcert.wallet

import id.walt.dcql.models.DcqlQuery
import id.walt.rpcert.wallet.RelyingPartyRegistrationCertificateVerifier.DecodedRegistrationCertificate
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.x509.CertificateDer

/**
 * Result of validating an OpenID4VP Authorization Request against a
 * Wallet-Relying Party Registration Certificate.
 */
sealed interface RegistrationValidationResult {
    val allowed: Boolean

    /** The registration certificate is valid and covers the requested DCQL query. */
    data class Allowed(
        val registrationCertificate: DecodedRegistrationCertificate,
    ) : RegistrationValidationResult {
        override val allowed = true
    }

    /** The registration certificate itself could not be verified (signature, x5c chain, validity, format). */
    data class InvalidRegistrationCertificate(val cause: Throwable) : RegistrationValidationResult {
        override val allowed = false
    }

    /** The registration certificate is valid, but does not cover the requested DCQL query. */
    data class RequestNotCovered(
        val registrationCertificate: DecodedRegistrationCertificate,
    ) : RegistrationValidationResult {
        override val allowed = false
    }

    /** The Authorization Request does not contain a DCQL query to check. */
    data class MissingDcqlQuery(val message: String) : RegistrationValidationResult {
        override val allowed = false
    }
}

/**
 * Wallet-side entry point: checks whether a verifier is allowed to request the information in
 * an Authorization Request's DCQL query, according to its registration certificate. Delegates to
 * [RelyingPartyRegistrationCertificateVerifier.verify] and [RegistrationCertificateDcqlMatcher.matchDcqlQuery].
 *
 * TODO: the registration certificate `status` claim (token status list) is not checked,
 * as we do not support status lists yet
 */
object RegistrationCertificateWalletValidator {

    suspend fun validate(
        authorizationRequest: AuthorizationRequest,
        registrationCertificateJwt: String,
        trustAnchors: List<CertificateDer>? = null,
        allowTrustedChainRoot: Boolean = false,
    ): RegistrationValidationResult {
        val dcqlQuery = authorizationRequest.dcqlQuery
            ?: return RegistrationValidationResult.MissingDcqlQuery(
                "Authorization Request contains no dcql_query to match against the registration certificate"
            )
        return validate(dcqlQuery, registrationCertificateJwt, trustAnchors, allowTrustedChainRoot)
    }

    suspend fun validate(
        dcqlQuery: DcqlQuery,
        registrationCertificateJwt: String,
        trustAnchors: List<CertificateDer>? = null,
        allowTrustedChainRoot: Boolean = false,
    ): RegistrationValidationResult {
        val decoded = RelyingPartyRegistrationCertificateVerifier.verify(
            certificateJwt = registrationCertificateJwt,
            trustAnchors = trustAnchors,
            allowTrustedChainRoot = allowTrustedChainRoot,
        ).getOrElse { return RegistrationValidationResult.InvalidRegistrationCertificate(it) }

        val allowed = RegistrationCertificateDcqlMatcher.matchDcqlQuery(decoded.certificate, dcqlQuery)

        return if (allowed) {
            RegistrationValidationResult.Allowed(decoded)
        } else {
            RegistrationValidationResult.RequestNotCovered(decoded)
        }
    }
}
