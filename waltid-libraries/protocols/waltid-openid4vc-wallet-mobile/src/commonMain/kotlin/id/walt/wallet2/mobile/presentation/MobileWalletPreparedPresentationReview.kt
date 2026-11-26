package id.walt.wallet2.mobile.presentation

/**
 * Effective claims for a selection, prepared without signing or consuming the retained request.
 *
 * @property credentials Effective claim contributions, kept separate for each query and credential.
 */
public data class MobileWalletPreparedPresentationReview(
    val credentials: List<MobileWalletPresentationCredentialClaims>,
)

/**
 * One query's credential contribution; received array indexes are not stored control paths.
 *
 * @property queryId Identifier of the DCQL credential query fulfilled by this contribution.
 * @property credentialId Identifier of the stored credential supplying this contribution.
 * @property format Credential format identifier used by the presenter.
 * @property claimsJson JSON object containing the effective credential claims carried by this
 * contribution, before holder signing and response transport, including unavoidable extra values.
 * Locations marked [MobileWalletPresentationClaimLocation.isBinary] identify base64url strings.
 * @property wholeCredential Whether the presenter sends the credential in full rather than
 * selectively disclosing its claims.
 * @property locations Mappings from stored claim identities to positions in [claimsJson], with
 * the selection information needed to explain why each value is shared.
 */
public data class MobileWalletPresentationCredentialClaims(
    val queryId: String,
    val credentialId: String,
    val format: String,
    val claimsJson: String,
    val wholeCredential: Boolean,
    val locations: List<MobileWalletPresentationClaimLocation>,
)

/**
 * Claim provenance preserving object keys, numeric indexes and compacted received positions.
 *
 * @property sourcePathJson JSON array identifying the claim in the stored credential. String
 * elements are object keys and integer elements are array indexes; use this path for metadata.
 * @property receivedPathJson JSON array locating the value in the contribution's effective claims.
 * Its indexes can differ from the source when undisclosed array elements are omitted.
 * @property sharedBy Original SDK claim-selection keys whose disclosures or selected elements
 * carry this value. These are selection identifiers, not received claim paths.
 * @property alwaysCarried Whether the value is clear text outside any selectively disclosed
 * ancestor, or belongs to a credential that is shared in full.
 * @property isBinary Whether the value is an mdoc CBOR byte string. Its review JSON value is
 * unpadded base64url and the location has no per-byte child paths.
 */
public data class MobileWalletPresentationClaimLocation(
    val sourcePathJson: String,
    val receivedPathJson: String,
    val sharedBy: List<String>,
    val alwaysCarried: Boolean,
    val isBinary: Boolean = false,
)
