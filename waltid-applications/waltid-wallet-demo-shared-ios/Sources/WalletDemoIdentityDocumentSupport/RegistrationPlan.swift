import Foundation
import WalletSDK

/// One mdoc credential the wallet wants Apple to offer.
public struct DesiredRegistration: Equatable, Hashable, Sendable {
    /// `MobileDocumentRegistration.documentIdentifier`; the wallet's stable per-credential ID.
    public let documentIdentifier: String
    /// ISO mdoc doctype to register.
    public let documentType: String

    /// Creates a desired registration.
    public init(documentIdentifier: String, documentType: String) {
        self.documentIdentifier = documentIdentifier
        self.documentType = documentType
    }
}

/// One registration Apple currently holds.
public struct ExistingRegistration: Equatable, Hashable, Sendable {
    /// Document identifier Apple has on file.
    public let documentIdentifier: String
    /// Doctype Apple has on file.
    public let documentType: String

    /// Creates an existing registration.
    public init(documentIdentifier: String, documentType: String) {
        self.documentIdentifier = documentIdentifier
        self.documentType = documentType
    }
}

/// Deterministic set of Apple registration-store mutations.
public struct RegistrationPlan: Equatable, Sendable {
    /// Registrations to add, ordered by document identifier so the plan is reproducible.
    public let toAdd: [DesiredRegistration]
    /// Document identifiers to remove, ordered for the same reason.
    public let toRemove: [String]

    /// Whether applying this plan would change Apple's store.
    public var isEmpty: Bool { toAdd.isEmpty && toRemove.isEmpty }
}

/// Computes the add/remove diff between the wallet's desired state and Apple's actual store.
///
/// Pure on purpose: this is the only part of registration with interesting behaviour, and it can be
/// exercised without an iOS 26 device or a mocked framework.
///
/// A published projection is authoritative for the whole store, so any registration absent from it is
/// removed. That is safe because this integration is the only writer of Apple's store for this
/// provider and an unreadable projection never reaches here - ``desiredRegistrations(from:)`` fails
/// closed first.
///
/// - Parameters:
///   - desired: Desired registrations read from the wallet's shared projection state.
///   - existing: Registrations Apple currently holds.
///   - supportedDocumentTypes: Doctypes this build's entitlement actually grants. A desired doctype
///     outside this set is dropped rather than registered: Apple rejects the addition, and one
///     rejected doctype would otherwise abort reconciliation for the valid ones.
public func reconciliationPlan(
    desired: [DesiredRegistration],
    existing: [ExistingRegistration],
    supportedDocumentTypes: Set<String>
) -> RegistrationPlan {
    let registrable = desired.filter { supportedDocumentTypes.contains($0.documentType) }
    let desiredByIdentifier = Dictionary(
        registrable.map { ($0.documentIdentifier, $0) },
        uniquingKeysWith: { first, _ in first }
    )
    let existingByIdentifier = Dictionary(
        existing.map { ($0.documentIdentifier, $0) },
        uniquingKeysWith: { first, _ in first }
    )

    // A doctype change under an unchanged identifier is a replacement: Apple has no update
    // operation, so the stale registration is removed and the new one added.
    let toAdd = desiredByIdentifier.values
        .filter { existingByIdentifier[$0.documentIdentifier]?.documentType != $0.documentType }
        .sorted { $0.documentIdentifier < $1.documentIdentifier }
    let toRemove = existingByIdentifier.values
        .filter { desiredByIdentifier[$0.documentIdentifier]?.documentType != $0.documentType }
        .map(\.documentIdentifier)
        .sorted()

    return RegistrationPlan(toAdd: toAdd, toRemove: toRemove)
}

/// Turns a projection read from the App Group into the registrations to reconcile against, or `nil`.
///
/// Split out of ``IdentityDocumentRegistrationCoordinator`` because this is the fail-closed decision
/// and the coordinator around it only runs against Apple's actor on an authorized iOS 26 device.
///
/// - Parameter projection: What the shared App Group says about the active wallet.
/// - Returns: The wallet's desired registrations, or `nil` when no wallet has published yet and Apple's
///   store must be left exactly as it is. An empty array is a decision, not an absence: the wallet has
///   no presentable mdoc credential and its registrations have to go.
/// - Throws: ``IdentityDocumentSupportFailure/unreadableDesiredRegistrations(_:)`` when the projection
///   exists but cannot be decoded. Thrown rather than returned as `nil` because a corrupt container is a
///   bug worth surfacing, while a missing one is the normal state of a fresh install.
public func desiredRegistrations(
    from projection: DesiredRegistrationProjection
) throws -> [DesiredRegistration]? {
    switch projection {
    case .missing:
        return nil
    case .malformed(let reason):
        throw IdentityDocumentSupportFailure.unreadableDesiredRegistrations(reason)
    case .published(_, let registrations):
        return registrations.map {
            DesiredRegistration(documentIdentifier: $0.documentIdentifier, documentType: $0.documentType)
        }
    }
}
