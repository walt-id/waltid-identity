import Foundation
import WalletSDK

protocol DemoReaderTrustSettingsPersistence: AnyObject {
    func loadEncodedSettings() throws -> String?
    func saveEncodedSettings(_ encoded: String) throws
}

final class UserDefaultsDemoReaderTrustSettingsPersistence: DemoReaderTrustSettingsPersistence {
    private let defaults: UserDefaults?

    init(appGroupIdentifier: String) {
        defaults = UserDefaults(suiteName: appGroupIdentifier)
    }

    func loadEncodedSettings() throws -> String? {
        guard let defaults else { throw DemoReaderTrustSettingsPersistenceError.appGroupUnavailable }
        return defaults.string(forKey: Self.settingsKey)
    }

    func saveEncodedSettings(_ encoded: String) throws {
        guard let defaults else { throw DemoReaderTrustSettingsPersistenceError.appGroupUnavailable }
        defaults.set(encoded, forKey: Self.settingsKey)
    }

    static let settingsKey = "id.walt.walletdemo.sharing.readerTrustSettings"
}

final class InMemoryDemoReaderTrustSettingsPersistence: DemoReaderTrustSettingsPersistence {
    var encodedSettings: String?

    init(settings: ProximityReaderTrustSettings? = nil) {
        encodedSettings = try? settings.map(ProximityReaderTrustSettingsCodec.encode)
    }

    func loadEncodedSettings() throws -> String? { encodedSettings }

    func saveEncodedSettings(_ encoded: String) throws {
        encodedSettings = encoded
    }
}

enum DemoReaderTrustSettingsPersistenceError: LocalizedError {
    case appGroupUnavailable

    var errorDescription: String? {
        "Reader Authentication settings cannot access the wallet App Group"
    }
}

@MainActor
final class DemoReaderTrustSettingsController: ObservableObject {
    @Published private(set) var settings: ProximityReaderTrustSettings
    @Published private(set) var pendingImport: ProximityReaderTrustImportPreview?
    @Published private(set) var importInProgress = false
    @Published private(set) var errorMessage: String?

    private let persistence: any DemoReaderTrustSettingsPersistence

    init(persistence: any DemoReaderTrustSettingsPersistence) {
        self.persistence = persistence
        do {
            if let encoded = try persistence.loadEncodedSettings() {
                settings = try ProximityReaderTrustSettingsCodec.decode(encoded)
            } else {
                settings = ProximityReaderTrustSettings()
            }
        } catch {
            settings = ProximityReaderTrustSettings()
            errorMessage = "Stored Reader Authentication settings were invalid and were not loaded: \(error.localizedDescription)"
        }
    }

    /// Read exactly once when a new proximity session starts.
    func sessionSnapshot() -> ProximityReaderTrustSettings { settings }

    func setReaderPolicy(_ policy: ProximityStoredReaderPolicy) {
        persist(settings.updatingReaderPolicy(policy))
    }

    func prepareImport(sourceName: String, data: Data) async {
        guard !importInProgress else { return }
        importInProgress = true
        pendingImport = nil
        errorMessage = nil
        defer { importInProgress = false }
        do {
            pendingImport = try await ProximityReaderTrustSettingsCodec.prepareImport(
                sourceName: sourceName,
                data: data,
                existing: settings
            )
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func confirmImport() {
        guard let pendingImport else { return }
        if persist(pendingImport.resultingSettings) {
            self.pendingImport = nil
        }
    }

    func cancelImport() {
        pendingImport = nil
    }

    func removeReaderAuthority(id: String) {
        persist(settings.removingReaderTrustAnchor(id: id))
    }

    func removeRICALProvider(id: String) {
        persist(settings.removingRICALProvider(id: id))
    }

    func reset() {
        if persist(ProximityReaderTrustSettings()) {
            pendingImport = nil
        }
    }

    func dismissError() {
        errorMessage = nil
    }

    func reportImportError(_ message: String) {
        importInProgress = false
        pendingImport = nil
        errorMessage = message
    }

    @discardableResult
    private func persist(_ newSettings: ProximityReaderTrustSettings) -> Bool {
        do {
            try persistence.saveEncodedSettings(
                ProximityReaderTrustSettingsCodec.encode(newSettings)
            )
            settings = newSettings
            errorMessage = nil
            return true
        } catch {
            errorMessage = "Reader Authentication settings could not be saved: \(error.localizedDescription)"
            return false
        }
    }
}
