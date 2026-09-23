import Foundation
import WalletSDK
import WalletSDKKeychainRecovery

// Test-host entry point only. Compiles the actual optional Swift adapter against the SDK's
// portable contracts; it does not add a bridge or another product to either SDK.
@_cdecl("waltRecoveryKeychainExchange")
func exchange(_ namespace: UnsafePointer<CChar>) -> Int32 {
    let namespace = String(cString: namespace)
    let completed = DispatchSemaphore(value: 0)
    let result = ExchangeResult()
    Task {
        defer { completed.signal() }
        do {
            let provider = KeychainIdentityRecovery(namespace: namespace)
            let kotlinBytes = Data("synthetic-parity-fixture:kotlin".utf8)
            guard try await provider.retrieve(recordID: "from-kotlin") == kotlinBytes,
                  try await provider.store(recordID: "from-kotlin", data: kotlinBytes) == .acceptedLocally else { return }
            guard try await provider.store(recordID: "from-swift", data: Data("synthetic-parity-fixture:swift".utf8)) == .acceptedLocally else { return }
            result.succeeded = true
        } catch { /* The host reports a redacted failure; never log record bytes. */ }
    }
    guard completed.wait(timeout: .now() + 30) == .success else { return 1 }
    return result.succeeded ? 0 : 1
}

// The semaphore provides synchronization between the asynchronous adapter and host caller.
private final class ExchangeResult: @unchecked Sendable {
    var succeeded = false
}
