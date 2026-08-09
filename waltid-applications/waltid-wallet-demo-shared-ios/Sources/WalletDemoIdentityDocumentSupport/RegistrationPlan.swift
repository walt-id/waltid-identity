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
/// exercised without an iOS 26 device or a mocked framework. The Apple store wrapper around it stays
/// small enough to be verified by inspection.
///
/// Removal is restricted to identifiers this integration owns. Apple's store is per-provider, but a
/// registration written by an older build of the same app - or by a future feature - must not be
/// deleted merely because it is absent from the current desired state, since removing a registration
/// makes the platform stop offering a document that may still be presentable.
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
        .filter { isManaged($0.documentIdentifier) }
        .filter { desiredByIdentifier[$0.documentIdentifier]?.documentType != $0.documentType }
        .map(\.documentIdentifier)
        .sorted()

    return RegistrationPlan(toAdd: toAdd, toRemove: toRemove)
}

/// Turns a projection read from the App Group into the registrations to reconcile against, or `nil`.
///
/// Split out of ``IdentityDocumentRegistrationCoordinator`` because this is the fail-closed decision,
/// and the coordinator around it can only run against Apple's actor on an authorized iOS 26 device.
/// The distinction it makes is not cosmetic: ``reconciliationPlan(desired:existing:supportedDocumentTypes:)``
/// removes every managed registration absent from `desired`, so reading "I could not find out what the
/// wallet holds" as an empty desired set would unregister documents the wallet can still present.
///
/// - Parameter projection: What the shared App Group says about the active wallet.
/// - Returns: The wallet's desired registrations, or `nil` when no wallet has published yet and Apple's
///   store must be left exactly as it is. An empty array is a decision, not an absence: the wallet has
///   no presentable mdoc credential and its managed registrations have to go.
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

/// Prefix the wallet core gives every per-credential registry entry identifier.
///
/// It marks a registration as owned by this integration; see ``reconciliationPlan(desired:existing:supportedDocumentTypes:)``.
public let managedRegistrationIdentifierPrefix = "dc-"

private func isManaged(_ documentIdentifier: String) -> Bool {
    documentIdentifier.hasPrefix(managedRegistrationIdentifierPrefix)
}
