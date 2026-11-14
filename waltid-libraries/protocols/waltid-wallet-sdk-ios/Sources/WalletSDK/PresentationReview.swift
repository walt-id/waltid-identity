import Foundation

/// Effective credential claims for one selected query, prepared without signing or transport.
public struct PreparedPresentationReview: Equatable, Sendable {
    /// Effective contributions kept separate for each selected query and credential.
    public let credentials: [PresentationCredentialClaims]

    /// Creates a read-only review of the selected credential contributions.
    /// - Parameter credentials: Effective claims and provenance for each contribution.
    public init(credentials: [PresentationCredentialClaims]) {
        self.credentials = credentials
    }
}

/// Keep query contributions separate: received array positions are not stored claim identities.
public struct PresentationCredentialClaims: Equatable, Sendable {
    /// Identifier of the DCQL credential query fulfilled by this contribution.
    public let queryID: String
    /// Identifier of the stored credential supplying this contribution.
    public let credentialID: String
    /// Credential format identifier used by the presenter.
    public let format: String
    /// JSON object containing the effective claims, including unavoidable additional values.
    public let claimsJSON: String
    /// Whether the presenter sends the credential in full instead of selectively disclosing claims.
    public let wholeCredential: Bool
    /// Original and received claim paths together with the reasons each value is included.
    public let locations: [PresentationClaimLocation]

    /// Creates one query's effective credential contribution.
    /// - Parameters:
    ///   - queryID: Identifier of the fulfilled DCQL credential query.
    ///   - credentialID: Identifier of the selected stored credential.
    ///   - format: Credential format identifier used for presentation.
    ///   - claimsJSON: JSON object containing the claims carried by this contribution.
    ///   - wholeCredential: Whether the credential is shared in full.
    ///   - locations: Source/received path mappings and sharing provenance for the claims.
    public init(queryID: String, credentialID: String, format: String, claimsJSON: String,
                wholeCredential: Bool, locations: [PresentationClaimLocation]) {
        self.queryID = queryID; self.credentialID = credentialID; self.format = format
        self.claimsJSON = claimsJSON; self.wholeCredential = wholeCredential; self.locations = locations
    }
}

/// Typed JSON-array paths retain object keys and original versus compacted array positions.
public struct PresentationClaimLocation: Equatable, Sendable {
    /// JSON-array path in the stored credential, preserving string keys and integer indexes.
    public let sourcePathJSON: String
    /// JSON-array path in the effective claims; array indexes may be compacted after disclosure.
    public let receivedPathJSON: String
    /// Original SDK selection identifiers whose disclosures or selected elements carry this value.
    public let sharedBy: [String]
    /// Whether the value is outside selectively disclosed ancestors or belongs to a credential shared in full.
    public let alwaysCarried: Bool

    /// Creates the provenance mapping for one effective claim value.
    /// - Parameters:
    ///   - sourcePathJSON: JSON-array path identifying the original stored claim.
    ///   - receivedPathJSON: JSON-array path locating the value in the effective claims.
    ///   - sharedBy: Original selection identifiers carrying the value, rather than received paths.
    ///   - alwaysCarried: Whether the value is carried independently of selective-disclosure choices.
    public init(sourcePathJSON: String, receivedPathJSON: String, sharedBy: [String], alwaysCarried: Bool) {
        self.sourcePathJSON = sourcePathJSON; self.receivedPathJSON = receivedPathJSON
        self.sharedBy = sharedBy; self.alwaysCarried = alwaysCarried
    }
}
