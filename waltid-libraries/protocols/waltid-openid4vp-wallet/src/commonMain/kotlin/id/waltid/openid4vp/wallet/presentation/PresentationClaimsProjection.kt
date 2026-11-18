package id.waltid.openid4vp.wallet.presentation

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.credentials.utils.JwtUtils
import id.walt.dcql.DcqlMatcher
import id.waltid.openid4vp.wallet.WalletPresentationFormatRegistry
import kotlinx.serialization.json.*

/** Credential claims carried by a presenter, before holder signing and response transport. */
data class PresentationClaimLocation(
    val sourcePath: List<JsonElement>,
    val receivedPath: List<JsonElement>,
    /** Original SDK claim-selection keys whose disclosures carry this value. */
    val sharedBy: List<String> = emptyList(),
    /** True for clear values outside any selectively disclosed ancestor, or a whole credential. */
    val alwaysCarried: Boolean = true,
)

class PresentationClaimsProjection(
    val claims: JsonObject,
    val wholeCredential: Boolean,
    val locations: List<PresentationClaimLocation> = identityLocations(claims),
) {
    companion object {
        fun prepare(credential: DigitalCredential, match: DcqlMatcher.DcqlMatchResult): PresentationClaimsProjection =
            when (WalletPresentationFormatRegistry.resolve(credential.format)) {
                WalletPresentationFormatRegistry.SupportedFormat.DC_SD_JWT ->
                    SdJwtVcPresenter.preview(credential, match)
                WalletPresentationFormatRegistry.SupportedFormat.JWT_VC_JSON -> {
                    // Match the W3C presenter's SD mechanism decision exactly.
                    val selective = credential is SelectivelyDisclosableVerifiableCredential && !credential.disclosables.isNullOrEmpty()
                    if (selective) SdJwtVcPresenter.preview(credential, match)
                    else {
                        val payload = JwtUtils.parseJwt(requireNotNull(credential.signed) { "Signed W3C VC JWT is missing" }).second
                        PresentationClaimsProjection(payload, true, identityLocations(payload, payload["vc"] is JsonObject))
                    }
                }
                WalletPresentationFormatRegistry.SupportedFormat.MSO_MDOC -> {
                    val claims = MdocPresenter.previewClaims(credential, match)
                    val selected = match.originalQuery.claims.orEmpty().filter {
                        it.path.joinToString(".") in match.selectedDisclosures.orEmpty()
                    }
                    PresentationClaimsProjection(claims, false, identityLocations(claims).map { location ->
                        location.copy(alwaysCarried = false, sharedBy = selected.filter { query ->
                            location.sourcePath.take(query.path.size) == query.path
                        }.map { it.path.joinToString(".") })
                    })
                }
                else -> error("A reliable claim preview is unavailable for credential format ${credential.format}")
            }
    }
}

private fun identityLocations(claims: JsonObject, w3cWrapped: Boolean = false): List<PresentationClaimLocation> = buildList {
    fun visit(value: JsonElement, source: List<JsonElement>, received: List<JsonElement>) {
        add(PresentationClaimLocation(source, received))
        when (value) {
            is JsonObject -> value.forEach { (key, child) ->
                val childSource = if (w3cWrapped && received.isEmpty() && key == "vc") emptyList() else source + JsonPrimitive(key)
                visit(child, childSource, received + JsonPrimitive(key))
            }
            is JsonArray -> value.forEachIndexed { index, child -> visit(child, source + JsonPrimitive(index), received + JsonPrimitive(index)) }
            else -> Unit
        }
    }
    visit(claims, emptyList(), emptyList())
}
