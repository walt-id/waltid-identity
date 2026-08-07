import Foundation
import IdentityDocumentServices
import WalletSDK

enum IdentityDocumentSharedConfiguration {
    static let appGroupIdentifier = "group.id.walt.wallet.demo"
    static let keychainAccessGroupSuffix = "id.walt.wallet.shared"
    static let documentTypesKey = "id.walt.wallet.identity-document-types"
    static let registryIDKey = "id.walt.wallet.identity-document-registry-id"
    static let registrationIDsKey = "id.walt.wallet.identity-document-registration-ids"
    static let supportedDocumentTypes: Set<String> = [
        "org.iso.18013.5.1.mDL",
        "eu.europa.ec.eudi.pid.1",
        "eu.europa.ec.eudi.photoid.1",
    ]

    static var keychainAccessGroup: String? {
        Bundle.main.object(forInfoDictionaryKey: "WALTKeychainAccessGroup") as? String
    }
}

@available(iOS 26.0, *)
enum IdentityDocumentRegistrationCoordinator {
    static func update() async throws {
        guard let defaults = UserDefaults(suiteName: IdentityDocumentSharedConfiguration.appGroupIdentifier) else {
            throw RegistrationFailure.sharedContainerUnavailable
        }
        let store = IdentityDocumentProviderRegistrationStore()
        let status = await store.status
        DigitalCredentialRegistrationStorage.persist(
            status: status,
            appGroupIdentifier: IdentityDocumentSharedConfiguration.appGroupIdentifier
        )
        guard status == .authorized else { return }

        let requestedTypes = Set(
            defaults.stringArray(forKey: IdentityDocumentSharedConfiguration.documentTypesKey) ?? []
        )
        let documentTypes = requestedTypes
            .intersection(IdentityDocumentSharedConfiguration.supportedDocumentTypes)
            .sorted()
        let registryID = defaults.string(forKey: IdentityDocumentSharedConfiguration.registryIDKey) ?? "empty"
        let desiredRegistrations = documentTypes.map { documentType in
            (
                documentType,
                "id.walt.wallet.\(registryID).\(Data(documentType.utf8).base64EncodedString())"
            )
        }
        let desiredIDs = Set(desiredRegistrations.map { $0.1 })
        let existingIDs = Set(try await store.registrations.map(\.documentIdentifier))

        for (documentType, identifier) in desiredRegistrations where !existingIDs.contains(identifier) {
            try await store.addRegistration(
                MobileDocumentRegistration(
                    mobileDocumentType: documentType,
                    supportedAuthorityKeyIdentifiers: [],
                    documentIdentifier: identifier
                )
            )
        }

        let previousIDs = Set(
            defaults.stringArray(forKey: IdentityDocumentSharedConfiguration.registrationIDsKey) ?? []
        )
        for identifier in previousIDs.subtracting(desiredIDs).intersection(existingIDs) {
            try await store.removeRegistration(forDocumentIdentifier: identifier)
        }
        defaults.set(desiredRegistrations.map { $0.1 }, forKey: IdentityDocumentSharedConfiguration.registrationIDsKey)
    }

    enum RegistrationFailure: Error {
        case sharedContainerUnavailable
    }
}
