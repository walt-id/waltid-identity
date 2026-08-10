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

/// What reconciliation must do about the provider's authorization status before touching Apple's store.
///
/// Split out from the reconciler because the status is only readable from Apple's actor, which no test
/// can construct, and because getting this wrong deadlocks silently rather than failing.
public enum AuthorizationStep: Equatable, Sendable {
    /// Register the first supported document to raise Apple's prompt; the status decides nothing yet.
    case requestAuthorization
    /// Already authorized: diff and apply as usual.
    case reconcile
    /// Declined or unsupported. Apple's store must be left exactly as it is.
    case leaveAlone
}

/// Decides whether an undetermined provider should be prompted, reconciled, or left alone.
///
/// `notDetermined` must *register* rather than wait: registering is what raises Apple's authorization
/// prompt, so a reconciler that requires `authorized` first can never leave `notDetermined` at all.
///
/// - Parameters:
///   - isAuthorized: Whether the status is `authorized`.
///   - isUndetermined: Whether the status is `notDetermined`, i.e. the user has not answered yet.
public func authorizationStep(isAuthorized: Bool, isUndetermined: Bool) -> AuthorizationStep {
    if isAuthorized { return .reconcile }
    if isUndetermined { return .requestAuthorization }
    return .leaveAlone
}

/// Computes the add/remove diff between the wallet's desired state and Apple's actual store.
///
/// - Parameters:
///   - desired: Desired registrations read from the wallet's shared projection state.
///   - existing: Registrations Apple currently holds.
///   - supportedDocumentTypes: Doctypes this build's entitlement actually grants. A desired doctype
///     outside this set is dropped rather than registered, because Apple rejects the addition and one
///     rejected doctype would abort reconciliation for the valid ones.
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
/// A missing projection is not an empty desired state; only a published projection may remove
/// registrations.
///
/// - Parameter projection: What the shared App Group says about the active wallet.
/// - Returns: The wallet's desired registrations, or `nil` when no wallet has published yet and Apple's
///   store must be left exactly as it is.
/// - Throws: ``IdentityDocumentSupportFailure/unreadableDesiredRegistrations(_:)`` when the projection
///   exists but cannot be decoded.
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
