package id.walt.wallet2.handlers

import dev.whyoleg.cryptography.random.CryptographyRandom
import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.waltid.openid4vp.wallet.presentation.PresentationClaimsProjection
import id.waltid.openid4vp.wallet.presentation.PresentationClaimLocation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.kotlincrypto.hash.sha2.SHA256

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

/** An immutable selection review. Echo [revision] only after displaying these effective claims. */
class PreparedPresentationReview internal constructor(
    val revision: String,
    val credentials: List<PresentationCredentialClaims>,
)

/** Kept exclusively inside the wallet-bound preview lease; no request URL is fetched again. */
internal class RetainedPresentationReview {
    private data class Binding(
        val credentials: Set<PresentationCredentialSelection>,
        val disclosures: Set<PresentationDisclosureSelection>?,
        val storedCredentials: Map<String, String>,
        val keyId: String?,
        val holderDid: String?,
    )

    private var prepared: Pair<Binding, PreparedPresentationReview>? = null
    private var reviewRequired = false

    fun invalidate() {
        reviewRequired = true
        prepared = null
    }

    fun prepare(
        matches: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        credentials: List<PresentationCredentialSelection>,
        disclosures: List<PresentationDisclosureSelection>?,
        keyId: String?,
        holderDid: String?,
    ): PreparedPresentationReview {
        invalidate()
        val binding = binding(matches, credentials, disclosures, keyId, holderDid)
        val contributions = matches.flatMap { (queryId, results) -> results.map { match ->
            val raw = match.credential as RawDcqlCredential
            val credential = raw.originalCredential as DigitalCredential
            val projection = PresentationClaimsProjection.prepare(credential, match)
            PresentationCredentialClaims(queryId, raw.id, credential.format, projection.claims, projection.wholeCredential, projection.locations)
        } }
        return PreparedPresentationReview(CryptographyRandom.nextBytes(32).encodeToBase64Url(), contributions).also {
            prepared = binding to it
        }
    }

    fun confirm(
        revision: String?,
        matches: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        credentials: List<PresentationCredentialSelection>,
        disclosures: List<PresentationDisclosureSelection>?,
        keyId: String?,
        holderDid: String?,
    ) {
        if (!reviewRequired && revision == null) return // Existing clients retain their released flow.
        val snapshot = prepared
        if (revision == null || snapshot == null || revision != snapshot.second.revision ||
            snapshot.first != binding(matches, credentials, disclosures, keyId, holderDid)) {
            prepared = null // Restoring old choices after a stale attempt cannot revive its acknowledgment.
            error("Information to share has changed; review the current selection before submitting.")
        }
    }

    private fun binding(
        matches: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
        credentials: List<PresentationCredentialSelection>,
        disclosures: List<PresentationDisclosureSelection>?,
        keyId: String?,
        holderDid: String?,
    ) = Binding(credentials.toSet(), disclosures?.toSet(), matches.values.flatten().associate { match ->
        val raw = match.credential as RawDcqlCredential
        raw.id to SHA256().digest(Json.encodeToString(DigitalCredential.serializer(),
            raw.originalCredential as DigitalCredential).encodeToByteArray()).encodeToBase64Url()
    }, keyId, holderDid)
}
