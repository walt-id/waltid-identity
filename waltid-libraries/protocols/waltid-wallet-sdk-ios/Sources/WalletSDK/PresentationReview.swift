import Foundation

/// Effective credential claims for one selected query, prepared without signing or transport.
public struct PreparedPresentationReview: Equatable, Sendable {
    public let revision: String
    public let credentials: [PresentationCredentialClaims]
    public init(revision: String, credentials: [PresentationCredentialClaims]) {
        self.revision = revision
        self.credentials = credentials
    }
}

/// Keep query contributions separate: received array positions are not stored claim identities.
public struct PresentationCredentialClaims: Equatable, Sendable {
    public let queryID: String
    public let credentialID: String
    public let format: String
    public let claimsJSON: String
    public let wholeCredential: Bool
    public let locations: [PresentationClaimLocation]
    public init(queryID: String, credentialID: String, format: String, claimsJSON: String,
                wholeCredential: Bool, locations: [PresentationClaimLocation]) {
        self.queryID = queryID; self.credentialID = credentialID; self.format = format
        self.claimsJSON = claimsJSON; self.wholeCredential = wholeCredential; self.locations = locations
    }
}

/// Typed JSON-array paths retain object keys and original versus compacted array positions.
public struct PresentationClaimLocation: Equatable, Sendable {
    public let sourcePathJSON: String
    public let receivedPathJSON: String
    public let sharedBy: [String]
    public let alwaysCarried: Bool
    public init(sourcePathJSON: String, receivedPathJSON: String, sharedBy: [String], alwaysCarried: Bool) {
        self.sourcePathJSON = sourcePathJSON; self.receivedPathJSON = receivedPathJSON
        self.sharedBy = sharedBy; self.alwaysCarried = alwaysCarried
    }
}
