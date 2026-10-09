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
    var availability: DemoBiometricAvailability { get }
    func authenticate(reason: String) async -> DemoBiometricResult
}

extension DemoBiometricAuthenticator {
    var kind: DemoBiometricKind { .generic }
    var isAvailable: Bool { availability == .available }
}

enum DemoBiometricAvailability: Equatable {
    case available, notEnrolled, deviceCredentialNotSet, lockedOut, unavailable, unsupported

    var authenticationResult: DemoBiometricResult { self == .lockedOut ? .lockedOut : .unavailable }
    var offersSettings: Bool { self == .notEnrolled || self == .deviceCredentialNotSet || self == .unavailable }
    func explanation(kind: DemoBiometricKind = .generic) -> String {
        switch self {
        case .available: "Biometrics are ready."
        case .notEnrolled: "Set up biometrics in device settings, then try again."
        case .deviceCredentialNotSet: "Set up a device passcode in Settings to use biometrics."
        case .lockedOut: "Biometrics are locked. Unlock your device with its passcode, then try again."
        case .unsupported: "This device does not support biometric authentication."
        case .unavailable:
            kind == .faceID ? "Face ID is unavailable. Check this app’s Face ID access in Settings."
                : "Biometrics are unavailable. Check device settings or try again later."
        }
    }
}

struct UnavailableDemoBiometricAuthenticator: DemoBiometricAuthenticator {
    var availability: DemoBiometricAvailability { .unsupported }

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
    var availability: DemoBiometricAvailability {
        onMainThread { Self.availability(context: LAContext()) }
    }

    func authenticate(reason: String) async -> DemoBiometricResult {
        await authenticateOnMain(reason: reason)
    }

    @MainActor
    private func authenticateOnMain(reason: String) async -> DemoBiometricResult {
        let context = LAContext()
        let availability = Self.availability(context: context)
        guard availability == .available else { return availability.authenticationResult }
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

    static func classifyAvailability(_ error: NSError?, biometry: LABiometryType) -> DemoBiometricAvailability {
        guard let error, error.domain == LAError.errorDomain else { return .unavailable }
        switch LAError.Code(rawValue: error.code) {
        case .biometryNotEnrolled: return .notEnrolled
        case .passcodeNotSet: return .deviceCredentialNotSet
        case .biometryLockout: return .lockedOut
        case .biometryNotAvailable: return biometry == .none ? .unsupported : .unavailable
        default: return .unavailable
        }
    }

    private static func availability(context: LAContext) -> DemoBiometricAvailability {
        var error: NSError?
        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) { return .available }
        return classifyAvailability(error, biometry: context.biometryType)
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
