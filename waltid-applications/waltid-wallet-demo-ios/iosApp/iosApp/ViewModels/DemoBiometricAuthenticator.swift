import LocalAuthentication

enum DemoBiometricResult: Equatable {
    case succeeded
    case failed
    case cancelled
    case unavailable
}

protocol DemoBiometricAuthenticator {
    var isAvailable: Bool { get }
    func authenticate(reason: String) async -> DemoBiometricResult
}

struct UnavailableDemoBiometricAuthenticator: DemoBiometricAuthenticator {
    var isAvailable: Bool { false }

    func authenticate(reason: String) async -> DemoBiometricResult {
        .unavailable
    }
}

struct LocalAuthenticationBiometricAuthenticator: DemoBiometricAuthenticator {
    var isAvailable: Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }

    func authenticate(reason: String) async -> DemoBiometricResult {
        let context = LAContext()
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else {
            return .unavailable
        }
        do {
            let success = try await context.evaluatePolicy(
                .deviceOwnerAuthenticationWithBiometrics,
                localizedReason: reason
            )
            return success ? .succeeded : .failed
        } catch let error as LAError {
            switch error.code {
            case .userCancel, .userFallback, .systemCancel, .appCancel:
                return .cancelled
            case .biometryNotAvailable, .biometryNotEnrolled:
                return .unavailable
            default:
                return .failed
            }
        } catch {
            return .failed
        }
    }
}
