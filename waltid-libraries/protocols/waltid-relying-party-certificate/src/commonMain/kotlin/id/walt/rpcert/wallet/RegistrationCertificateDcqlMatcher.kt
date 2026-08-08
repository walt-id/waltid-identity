package id.walt.rpcert.wallet

import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.CredentialQueryMeta
import id.walt.dcql.models.meta.GenericMeta
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.dcql.models.meta.NoMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.rpcert.models.Claim
import id.walt.rpcert.models.RegistrationCertificateCredential
import id.walt.rpcert.models.RelyingPartyRegistrationCertificate

/**
 * Matches a DCQL query (from an OpenID4VP Authorization Request) against the credentials
 * registered in a Wallet-Relying Party's registration certificate.
 */
object RegistrationCertificateDcqlMatcher {

    // Strictest interpretation: every credential query must be covered, including any
    // credential_sets alternatives — a verifier must not query anything it did not register.
    fun matchDcqlQuery(
        certificate: RelyingPartyRegistrationCertificate,
        dcqlQuery: DcqlQuery,
    ): Boolean = dcqlQuery.credentials.all { credentialQuery ->
        credentialQueryIsCovered(certificate.credentials, credentialQuery)
    }

    private fun credentialQueryIsCovered(
        registered: List<RegistrationCertificateCredential>,
        query: CredentialQuery,
    ): Boolean = registered
        .filter { it.format == query.format }
        .any { isCovered(it, query) }

    /** True if [candidate] fully covers [query]. */
    private fun isCovered(
        candidate: RegistrationCertificateCredential,
        query: CredentialQuery,
    ): Boolean = metaCovers(candidate.meta, query.meta) && claimsCovers(candidate.claim, query.claims)

    /** True if [registered] meta constraints cover [queried] meta constraints ([NoMeta] covers any query). */
    private fun metaCovers(
        registered: CredentialQueryMeta,
        queried: CredentialQueryMeta,
    ): Boolean = when (registered) {
        is NoMeta -> true

        is MsoMdocMeta -> queried is MsoMdocMeta && queried.doctypeValue == registered.doctypeValue

        is SdJwtVcMeta -> queried is SdJwtVcMeta && registered.vctValues.containsAll(queried.vctValues)

        is JwtVcJsonMeta -> queried is JwtVcJsonMeta &&
            queried.typeValues.all { queriedTypes -> queriedTypes in registered.typeValues }

        is GenericMeta -> queried == registered
    }

    /** True if [registered] claims cover [queried] claims (no [registered] list means the whole credential is registered). */
    private fun claimsCovers(
        registered: List<Claim>?,
        queried: List<ClaimsQuery>?,
    ): Boolean {
        // Registered for the whole credential: any claim selection is allowed
        if (registered == null) return true

        // Query requests the whole credential, but only specific claims are registered
        if (queried == null) return false

        return queried.all { claimQuery ->
            val registeredClaim = registered.find { it.path == claimQuery.path } ?: return@all false

            registeredClaim.values?.let { allowedValues ->
                val queriedValues = claimQuery.values ?: return@all false
                allowedValues.containsAll(queriedValues)
            } ?: true
        }
    }
}
