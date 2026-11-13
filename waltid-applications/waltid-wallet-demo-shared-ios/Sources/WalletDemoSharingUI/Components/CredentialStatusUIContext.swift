import SwiftUI
import WalletSDK

/// Local observation lifecycle, separate from every issuer status and network-check result.
public enum CredentialStatusReadState: Equatable, Sendable {
    case loading, available, unavailable
    public var awaitingRead: Self { self == .unavailable ? .unavailable : .loading }
}

/// Status is wallet chrome, independent of issuer artwork and shared claims.
public struct CredentialStatusUIContext {
    public var values: [String: CredentialStatus]
    public var refreshing: Set<String>
    public var check: ((String) -> Void)?
    public var storeIndices: [String: Int]
    public var readState: CredentialStatusReadState
    public var available: Bool { readState == .available }
    public var contextKey: AnyHashable?
    public var checking: Bool
    /// Preserve local facts while withholding the host's networking capability.
    public var readOnly: Self { var value = self; value.check = nil; return value }
    public init(values: [String: CredentialStatus] = [:], refreshing: Set<String> = [], check: ((String) -> Void)? = nil,
                storeIndices: [String: Int] = [:], readState: CredentialStatusReadState = .available, contextKey: AnyHashable? = nil, checking: Bool = false) {
        self.values = values; self.refreshing = refreshing; self.check = check
        self.storeIndices = storeIndices; self.readState = readState; self.contextKey = contextKey
        self.checking = checking
    }
    public init(snapshot: CredentialStatusSnapshot, contextKey: AnyHashable? = nil) {
        let unique = Dictionary(grouping: snapshot.credentials, by: \.credentialID).values.compactMap { $0.count == 1 ? $0.first : nil }
        self.init(values: Dictionary(uniqueKeysWithValues: unique.map { ($0.credentialID, $0.value) }),
                  refreshing: snapshot.refreshingCredentialIDs,
                  storeIndices: Dictionary(uniqueKeysWithValues: unique.map { ($0.credentialID, $0.storeIndex) }),
                  contextKey: contextKey)
    }
}
private struct CredentialStatusUIKey: EnvironmentKey { static let defaultValue = CredentialStatusUIContext(readState: .loading) }
public extension EnvironmentValues {
    var credentialStatusUI: CredentialStatusUIContext {
        get { self[CredentialStatusUIKey.self] }
        set { self[CredentialStatusUIKey.self] = newValue }
    }
}
