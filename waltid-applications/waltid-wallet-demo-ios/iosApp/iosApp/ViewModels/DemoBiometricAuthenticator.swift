import LocalAuthentication

enum DemoBiometricResult: Equatable {
    case succeeded
    case failed
}

protocol DemoBiometricAuthenticator {
    var isAvailable: Bool { get }
    func authenticate(reason: String) async -> DemoBiometricResult
}

struct UnavailableDemoBiometricAuthenticator: DemoBiometricAuthenticator {
    var isAvailable: Bool { false }

    func authenticate(reason: String) async -> DemoBiometricResult {
        .failed
    }
}

struct LocalAuthenticationBiometricAuthenticator: DemoBiometricAuthenticator {
    var isAvailable: Bool {
        onMainThread { Self.canEvaluateBiometrics() }
    }

    func authenticate(reason: String) async -> DemoBiometricResult {
        await authenticateOnMain(reason: reason)
    }

    @MainActor
    private func authenticateOnMain(reason: String) async -> DemoBiometricResult {
        let context = LAContext()
        guard Self.canEvaluateBiometrics(context: context) else {
            return .failed
        }
        do {
            let success = try await context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: reason
            )
            return success ? .succeeded : .failed
        } catch {
            return .failed
        }
    }

    private static func canEvaluateBiometrics(context: LAContext = LAContext()) -> Bool {
        var error: NSError?
        return context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
    }
}

private func onMainThread<T>(_ block: () -> T) -> T {
    if Thread.isMainThread {
        return block()
    }
    return DispatchQueue.main.sync(execute: block)
}
