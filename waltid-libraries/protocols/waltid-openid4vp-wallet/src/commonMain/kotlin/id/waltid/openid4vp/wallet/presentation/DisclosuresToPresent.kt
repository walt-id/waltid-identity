package id.waltid.openid4vp.wallet.presentation

import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher

/**
 * The disclosures of this credential to present for [matchResult]: those of the selected claims and of
 * the claims containing them, so the verifier can reach every selected claim (RFC 9901 §4.2.6).
 *
 * A [DcqlDisclosure] is resolved by its location; without one, by claim name. Array-element disclosures
 * have no name, so they are never resolved by name.
 */
internal fun SelectivelyDisclosableVerifiableCredential.disclosuresToPresent(
    matchResult: DcqlMatcher.DcqlMatchResult,
): List<SdJwtSelectiveDisclosure> {
    val storedDisclosures = disclosures.orEmpty()

    val requestedDisclosures = matchResult.disclosuresByPath.values.flatten()
        .ifEmpty { matchResult.selectedDisclosures?.values.orEmpty() }

    return requestedDisclosures.mapNotNull { requested ->
        when (requested) {
            is SdJwtSelectiveDisclosure -> requested
            is DcqlDisclosure -> when {
                requested.location != null -> storedDisclosures.find { it.location == requested.location }
                requested.name != null -> storedDisclosures.find { it.name == requested.name }
                else -> null
            }

            else -> null
        }
    }.distinctBy { it.encoded }
}
