package id.walt.wallet2.handlers

import id.walt.credentials.formats.DigitalCredential
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.waltid.openid4vp.wallet.presentation.PresentationClaimsProjection
import id.waltid.openid4vp.wallet.presentation.PresentationClaimLocation
import kotlinx.serialization.json.JsonObject

/** One actual presentation contribution. Different queries may send different partial arrays. */
data class PresentationCredentialClaims(
    val queryId: String,
    val credentialId: String,
    val format: String,
    /** Received positions, not stored-credential/control positions. Never merge arrays by these indices. */
    val claims: JsonObject,
    val wholeCredential: Boolean,
    val locations: List<PresentationClaimLocation>,
)

/** Effective claims for the current selection, prepared without signing or submitting. */
class PreparedPresentationReview internal constructor(
    val credentials: List<PresentationCredentialClaims>,
)

internal fun preparePresentationReview(
    matches: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
): PreparedPresentationReview = PreparedPresentationReview(matches.flatMap { (queryId, results) ->
    results.map { match ->
        val raw = match.credential as RawDcqlCredential
        val credential = raw.originalCredential as DigitalCredential
        val projection = PresentationClaimsProjection.prepare(credential, match)
        PresentationCredentialClaims(queryId, raw.id, credential.format, projection.claims,
            projection.wholeCredential, projection.locations)
    }
})
