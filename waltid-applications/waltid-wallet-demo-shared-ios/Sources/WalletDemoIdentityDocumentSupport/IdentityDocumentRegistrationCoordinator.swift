import Foundation
import OSLog
import WalletSDK
#if os(iOS)
import IdentityDocumentServices
#endif

/// The single place either demo applies its desired registrations to Apple's store.
///
/// Both host apps and the extension's `performRegistrationUpdates()` call this. Only these processes
/// may write Apple's store, and Apple's callback runs periodically rather than on change, so the host
/// also reconciles after a credential-set change and on becoming active - a status change carries no
/// notification.
///
/// Authorization is *requested by registering*, not granted beforehand: from `.notDetermined` the
/// first `addRegistration` presents Apple's prompt and the status becomes `.authorized` or
/// `.notAuthorized` according to the answer. Waiting for `.authorized` before registering therefore
/// deadlocks - the status can never leave `.notDetermined`. Settings is the recovery path afterwards.
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
        let step = authorizationStep(
            isAuthorized: status == .authorized,
            isUndetermined: status == .notDetermined
        )
        guard step != .leaveAlone else {
            // Desired state stays untouched in the App Group: the wallet's projection is
            // authoritative and must survive until the user decides. A declined provider is not
            // asked again here; re-enabling it is a Settings action.
            log.info("Provider registration unavailable (\(String(describing: status), privacy: .public)); leaving desired state in place")
            return RegistrationPlan(toAdd: [], toRemove: [])
        }

        // Fails closed on anything short of a published projection: the plan below may remove every
        // registration, so an absent or undecodable projection must not be read as "the wallet wants
        // nothing".
        guard let desired = try desiredRegistrations(
            from: DigitalCredentialRegistrationStorage.desiredRegistrations(
                appGroupIdentifier: namespace.appGroupIdentifier
            )
        ) else {
            log.info("No desired registration state in App Group \(namespace.appGroupIdentifier, privacy: .public); leaving Apple's registrations untouched")
            return RegistrationPlan(toAdd: [], toRemove: [])
        }

        if step == .requestAuthorization {
            guard try await requestAuthorization(store: store, registering: desired) != nil else {
                return RegistrationPlan(toAdd: [], toRemove: [])
            }
        }

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

    /// Asks for authorization by registering the first supported document, and reports the new status.
    ///
    /// Registering *is* the request: Apple presents its prompt during this call. The document is a
    /// real desired registration rather than a throwaway, so approving it leaves the store correct and
    /// the diff below simply finds it already present.
    ///
    /// - Returns: The status after the user answered, or `nil` when there is nothing to register or the
    ///   user declined - both cases leave Apple's store alone.
    private func requestAuthorization(
        store: IdentityDocumentProviderRegistrationStore,
        registering desired: [DesiredRegistration]
    ) async throws -> IdentityDocumentProviderRegistrationStore.Status? {
        // Only a doctype the entitlement grants can carry the request; anything else is rejected on
        // its own merits and would look like a declined authorization.
        guard let first = desired
            .filter({ namespace.supportedDocumentTypes.contains($0.documentType) })
            .min(by: { $0.documentIdentifier < $1.documentIdentifier })
        else {
            log.info("Authorization is undetermined and the wallet has nothing registrable; not prompting")
            return nil
        }

        do {
            log.info("Requesting provider authorization by registering \(first.documentIdentifier, privacy: .public)")
            try await store.addRegistration(
                MobileDocumentRegistration(
                    mobileDocumentType: first.documentType,
                    supportedAuthorityKeyIdentifiers: [],
                    documentIdentifier: first.documentIdentifier
                )
            )
        } catch IdentityDocumentProviderRegistrationStore.RegistrationError.notAuthorized {
            // A declined prompt is the user's answer, not a wallet failure.
            let declined = await store.status
            DigitalCredentialRegistrationStorage.persist(
                status: declined,
                appGroupIdentifier: namespace.appGroupIdentifier
            )
            log.info("Provider authorization declined (\(String(describing: declined), privacy: .public))")
            return nil
        }

        let granted = await store.status
        DigitalCredentialRegistrationStorage.persist(
            status: granted,
            appGroupIdentifier: namespace.appGroupIdentifier
        )
        guard granted == .authorized else {
            log.info("Registration succeeded but status is \(String(describing: granted), privacy: .public); leaving the rest alone")
            return nil
        }
        return granted
    }

    /// Reconciles from Apple's own registration-update callback, logging rather than throwing.
    ///
    /// `performRegistrationUpdates()` cannot throw, so on a device this log line is the only trace of
    /// a failure.
    public func reconcileFromPlatformCallback() async {
        do {
            let plan = try await reconcile()
            log.info("Reconciled registrations: +\(plan.toAdd.count, privacy: .public) -\(plan.toRemove.count, privacy: .public)")
        } catch {
            log.error("Registration reconciliation failed for App Group \(namespace.appGroupIdentifier, privacy: .public): \(error.localizedDescription, privacy: .public)")
        }
    }
}
