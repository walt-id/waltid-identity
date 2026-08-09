import Foundation
import OSLog
import WalletSDK
#if os(iOS)
import IdentityDocumentServices
#endif

/// The single place either demo applies its desired registrations to Apple's store.
///
/// Both the host app - after bootstrap, issuance, deletion, and on becoming active - and the
/// extension's `performRegistrationUpdates()` call this. A second implementation would let the two
/// entry points disagree about what is registered, which is exactly the failure that makes a wallet
/// disappear from the provider picker.
@available(iOS 26.0, *)
public struct IdentityDocumentRegistrationCoordinator: Sendable {
    private let namespace: IdentityDocumentNamespace
    private let log: Logger

    /// Creates a coordinator for one demo's cross-process namespace.
    public init(namespace: IdentityDocumentNamespace) {
        self.namespace = namespace
        self.log = Logger(subsystem: "id.walt.wallet.identity-document", category: "registration")
    }

    /// Publishes the wallet's desired registrations and returns what was changed.
    ///
    /// Errors propagate. Callers on the host-app side can decide how to surface them; the extension
    /// logs them, because Apple discards a thrown error from `performRegistrationUpdates()` and a
    /// silent `try?` there is indistinguishable from a wallet that has nothing to register.
    @discardableResult
    public func reconcile() async throws -> RegistrationPlan {
        let store = IdentityDocumentProviderRegistrationStore()
        let status = await store.status
        DigitalCredentialRegistrationStorage.persist(
            status: status,
            appGroupIdentifier: namespace.appGroupIdentifier
        )
        guard status == .authorized else {
            // Desired state stays untouched in the App Group: the wallet's projection is
            // authoritative and must survive until the user decides.
            log.info("Provider registration not authorized (\(String(describing: status), privacy: .public)); leaving desired state in place")
            return RegistrationPlan(toAdd: [], toRemove: [])
        }

        let desired = DigitalCredentialRegistrationStorage
            .desiredRegistrations(appGroupIdentifier: namespace.appGroupIdentifier)
            .map { DesiredRegistration(documentIdentifier: $0.documentIdentifier, documentType: $0.documentType) }
        let existing = try await store.registrations.map { registration in
            // `IdentityDocumentRegistration` exposes only the identifier. A registration that is not
            // a mobile document gets an unmatchable doctype, so the planner treats it as stale rather
            // than as satisfying a desired mdoc registration under the same identifier.
            ExistingRegistration(
                documentIdentifier: registration.documentIdentifier,
                documentType: (registration as? MobileDocumentRegistration)?.mobileDocumentType ?? ""
            )
        }
        let plan = reconciliationPlan(
            desired: desired,
            existing: existing,
            supportedDocumentTypes: namespace.supportedDocumentTypes
        )
        guard !plan.isEmpty else { return plan }

        // Removals run first so a doctype replacement under an unchanged identifier cannot collide
        // with the registration it replaces.
        for documentIdentifier in plan.toRemove {
            log.info("Removing registration \(documentIdentifier, privacy: .public)")
            try await store.removeRegistration(forDocumentIdentifier: documentIdentifier)
        }
        for registration in plan.toAdd {
            log.info("Adding registration \(registration.documentIdentifier, privacy: .public) for \(registration.documentType, privacy: .public)")
            try await store.addRegistration(
                MobileDocumentRegistration(
                    mobileDocumentType: registration.documentType,
                    supportedAuthorityKeyIdentifiers: [],
                    documentIdentifier: registration.documentIdentifier
                )
            )
        }
        return plan
    }

    /// Reconciles from Apple's own registration-update callback, logging rather than throwing.
    ///
    /// `performRegistrationUpdates()` cannot throw, and on a device the only trace of a failure here
    /// is this log line, so it carries the identifiers needed to tell "nothing to register" apart
    /// from "registration was rejected".
    public func reconcileFromPlatformCallback() async {
        do {
            let plan = try await reconcile()
            log.info("Reconciled registrations: +\(plan.toAdd.count, privacy: .public) -\(plan.toRemove.count, privacy: .public)")
        } catch {
            log.error("Registration reconciliation failed for App Group \(namespace.appGroupIdentifier, privacy: .public): \(error.localizedDescription, privacy: .public)")
        }
    }
}
