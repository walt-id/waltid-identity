import Foundation
import WalletSDK
#if os(iOS)
import Security

/// Only accessibility classes compatible with synchronizable Keychain records.
public enum SynchronizableKeychainAccessibility: Sendable {
    case whenUnlocked, afterFirstUnlock
}

/// Optional iOS recovery adapter. Local Keychain acceptance does not prove iCloud delivery;
/// cloud synchronization still depends on the user's iCloud Keychain configuration.
public actor KeychainIdentityRecovery: IdentityRecoveryProvider {
    public nonisolated let id: String
    public nonisolated let displayName = "iCloud Keychain recovery"
    private let service: String
    private let accessGroup: String?
    private let accessibility: SynchronizableKeychainAccessibility

    public init(namespace: String, accessGroup: String? = nil,
                accessibility: SynchronizableKeychainAccessibility = .whenUnlocked) {
        precondition(namespace.range(of: "^[A-Za-z0-9._-]{1,64}$", options: .regularExpression) != nil)
        precondition(accessGroup == nil || accessGroup?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false)
        self.id = "keychain:\(namespace)"
        self.service = "id.walt.wallet.identity.recovery.\(namespace)"
        self.accessGroup = accessGroup
        self.accessibility = accessibility
    }

    public func availability() async throws -> WalletRecoveryAvailability {
        var request = query()
        request[kSecUseAuthenticationUI as String] = kSecUseAuthenticationUIFail
        switch SecItemCopyMatching(request as CFDictionary, nil) {
        case errSecSuccess, errSecItemNotFound:
            return .available(protection: .operatingSystemEndToEnd, scope: .cloud)
        case errSecInteractionNotAllowed: return .unavailable(reason: "Unlock the device to access Keychain recovery")
        default: return .unavailable(reason: "Keychain recovery is unavailable")
        }
    }

    public func list() async throws -> [String] {
        var request = query()
        request[kSecMatchLimit as String] = kSecMatchLimitAll
        request[kSecReturnAttributes as String] = true
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return [] }
        try check(status)
        guard let records = result as? [[String: Any]] else { throw IdentityProviderError.rejected }
        return try records.map {
            guard let account = $0[kSecAttrAccount as String] as? String else { throw IdentityProviderError.rejected }
            return account
        }
    }

    public func store(recordID: String, data: Data) async throws -> WalletRecoveryReceipt {
        guard (1...4096).contains(data.count) else { throw IdentityProviderError.rejected }
        var request = try query(recordID: recordID)
        request[kSecAttrAccessible as String] = accessibility == .whenUnlocked ? kSecAttrAccessibleWhenUnlocked : kSecAttrAccessibleAfterFirstUnlock
        request[kSecValueData as String] = data
        let status = SecItemAdd(request as CFDictionary, nil)
        if status == errSecDuplicateItem {
            guard try read(recordID: recordID) == data else { throw IdentityProviderError.conflict }
        } else { try check(status) }
        return .acceptedLocally
    }

    public func retrieve(recordID: String) async throws -> Data? { try read(recordID: recordID) }

    public func delete(recordID: String) async throws -> WalletRecoveryReceipt {
        let status = SecItemDelete(try query(recordID: recordID) as CFDictionary)
        if status != errSecItemNotFound { try check(status) }
        return .acceptedLocally
    }

    private func read(recordID: String) throws -> Data? {
        var request = try query(recordID: recordID)
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        try check(status)
        guard let data = result as? Data, (1...4096).contains(data.count) else { throw IdentityProviderError.rejected }
        return data
    }

    private func query() -> [String: Any] {
        var result: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service, kSecAttrSynchronizable as String: true]
        if let accessGroup { result[kSecAttrAccessGroup as String] = accessGroup }
        return result
    }
    private func query(recordID: String) throws -> [String: Any] {
        guard recordID.range(of: "^[A-Za-z0-9._-]{1,128}$", options: .regularExpression) != nil else { throw IdentityProviderError.rejected }
        var result = query()
        result[kSecAttrAccount as String] = recordID
        return result
    }
    private func check(_ status: OSStatus) throws {
        switch status {
        case errSecSuccess: return
        case errSecInteractionNotAllowed, errSecAuthFailed, errSecUserCanceled: throw IdentityProviderError.interactionRequired
        case errSecMissingEntitlement, errSecParam: throw IdentityProviderError.rejected
        case errSecDuplicateItem: throw IdentityProviderError.conflict
        default: throw IdentityProviderError.temporarilyUnavailable
        }
    }
}
#endif
