import LocalAuthentication

enum DemoBiometricResult: Equatable {
    case succeeded
    case cancelled
    case unavailable
    case lockedOut
    case failed

    var fallbackMessage: String? {
        switch self {
        case .succeeded, .cancelled: return nil
        case .unavailable: return "Biometric unlock is unavailable. Use your PIN."
        case .lockedOut: return "Biometrics are locked. Use your PIN."
        case .failed: return "Biometric unlock failed. Use your PIN or try again."
        }
    }
}

enum DemoBiometricKind { case generic, faceID, touchID }

protocol DemoBiometricAuthenticator {
    var kind: DemoBiometricKind { get }
    var isAvailable: Bool { get }
    func authenticate(reason: String) async -> DemoBiometricResult
}

extension DemoBiometricAuthenticator { var kind: DemoBiometricKind { .generic } }

struct UnavailableDemoBiometricAuthenticator: DemoBiometricAuthenticator {
    var isAvailable: Bool { false }

    func authenticate(reason: String) async -> DemoBiometricResult {
        .unavailable
    }
}

struct LocalAuthenticationBiometricAuthenticator: DemoBiometricAuthenticator {
    var kind: DemoBiometricKind {
        onMainThread {
            let context = LAContext()
            _ = Self.canEvaluateBiometrics(context: context)
            switch context.biometryType {
            case .faceID: return .faceID
            case .touchID: return .touchID
            default: return .generic
            }
        }
    }
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
            return .unavailable
        }
        return await withTaskCancellationHandler {
            do {
                let success = try await context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason)
                return success ? .succeeded : .failed
            } catch {
                let error = error as NSError
                guard error.domain == LAError.errorDomain else { return .failed }
                switch LAError.Code(rawValue: error.code) {
                case .userCancel, .appCancel, .systemCancel, .userFallback: return .cancelled
                case .biometryLockout: return .lockedOut
                case .biometryNotAvailable, .biometryNotEnrolled: return .unavailable
                default: return .failed
                }
            }
        } onCancel: { context.invalidate() }
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
