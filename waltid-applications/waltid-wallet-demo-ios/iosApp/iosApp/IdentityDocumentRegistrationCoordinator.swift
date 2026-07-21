import Foundation
import IdentityDocumentServices

enum IdentityDocumentSharedConfiguration {
    static let appGroupIdentifier = "group.id.walt.wallet.demo"
    static let keychainAccessGroupSuffix = "id.walt.wallet.shared"
    static let documentTypesKey = "id.walt.wallet.identity-document-types"
    static let registryIDKey = "id.walt.wallet.identity-document-registry-id"
    static let registrationIDsKey = "id.walt.wallet.identity-document-registration-ids"

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
        print("[WalletE2E] Identity document registration status: \(status)")
        guard status == .authorized else { return }

        let previousIDs = defaults.stringArray(
            forKey: IdentityDocumentSharedConfiguration.registrationIDsKey
        ) ?? []
        for identifier in previousIDs {
            try await store.removeRegistration(forDocumentIdentifier: identifier)
        }

        let registryID = defaults.string(forKey: IdentityDocumentSharedConfiguration.registryIDKey) ?? "empty"
        let documentTypes = defaults.stringArray(
            forKey: IdentityDocumentSharedConfiguration.documentTypesKey
        ) ?? []
        let registrationIDs = documentTypes.sorted().map { documentType in
            "id.walt.wallet.\(registryID).\(Data(documentType.utf8).base64EncodedString())"
        }
        for (documentType, identifier) in zip(documentTypes.sorted(), registrationIDs) {
            try await store.addRegistration(
                MobileDocumentRegistration(
                    mobileDocumentType: documentType,
                    supportedAuthorityKeyIdentifiers: [],
                    documentIdentifier: identifier
                )
            )
        }
        defaults.set(registrationIDs, forKey: IdentityDocumentSharedConfiguration.registrationIDsKey)
    }

    enum RegistrationFailure: Error {
        case sharedContainerUnavailable
    }
}
