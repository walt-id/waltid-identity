import Foundation
import WalletSDK

protocol DemoReaderTrustSettingsPersistence: AnyObject, Sendable {
    func loadEncodedSettings() throws -> String?
    func saveEncodedSettings(_ encoded: String) throws
}

final class UserDefaultsDemoReaderTrustSettingsPersistence: DemoReaderTrustSettingsPersistence, @unchecked Sendable {
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

final class InMemoryDemoReaderTrustSettingsPersistence: DemoReaderTrustSettingsPersistence, @unchecked Sendable {
    private let lock = NSLock()
    private var stored: String?
    var encodedSettings: String? {
        get { lock.lock(); defer { lock.unlock() }; return stored }
        set { lock.lock(); defer { lock.unlock() }; stored = newValue }
    }

    init(settings: ProximityReaderTrustSettings? = nil) {
        stored = try? settings.map(ProximityReaderTrustSettingsCodec.encode)
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

enum ReaderTrustImportFileSelection: Equatable {
    case cancelled
    case selected(sourceName: String, data: Data)
}

enum ReaderTrustImportFileLoader {
    static func loadOffMain(_ result: Result<[URL], Error>) async throws -> ReaderTrustImportFileSelection {
        let selected = try await Task.detached { try load(result) }.value
        try Task.checkCancellation()
        return selected
    }

    static func load(
        _ result: Result<[URL], Error>
    ) throws -> ReaderTrustImportFileSelection {
        let urls: [URL]
        do {
            urls = try result.get()
        } catch {
            if error is CancellationError ||
                (error as? CocoaError)?.code == .userCancelled {
                return .cancelled
            }
            throw error
        }

        guard urls.count == 1, let url = urls.first else {
            throw ReaderTrustFileImportError.selectOneFile
        }
        let accessGranted = url.startAccessingSecurityScopedResource()
        defer { if accessGranted { url.stopAccessingSecurityScopedResource() } }
        let fileSize = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize
        if let fileSize, fileSize > ProximityReaderTrustSettingsCodec.maximumImportBytes {
            throw ReaderTrustFileImportError.fileTooLarge
        }
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        let data = try handle.read(upToCount: ProximityReaderTrustSettingsCodec.maximumImportBytes + 1) ?? Data()
        guard data.count <= ProximityReaderTrustSettingsCodec.maximumImportBytes else {
            throw ReaderTrustFileImportError.fileTooLarge
        }
        return .selected(sourceName: url.lastPathComponent, data: data)
    }
}

enum ReaderTrustFileImportError: LocalizedError {
    case selectOneFile
    case fileTooLarge

    var errorDescription: String? {
        switch self {
        case .selectOneFile: return "Select one Reader Authentication file"
        case .fileTooLarge: return "The imported file exceeds 1 MiB"
        }
    }
}

@MainActor
final class DemoReaderTrustSettingsController: ObservableObject {
    @Published private(set) var settings: ProximityReaderTrustSettings
    @Published private(set) var pendingImport: ProximityReaderTrustImportPreview?
    @Published private(set) var importInProgress = false
    @Published private(set) var errorMessage: String?

    @Published private(set) var loading = true
    private let persistence: any DemoReaderTrustSettingsPersistence
    private var loadTask: Task<Void, Never>?
    private var saveTask: Task<Void, Never>?
    private var importGeneration = 0
    private var pendingWrites = 0


    init(persistence: any DemoReaderTrustSettingsPersistence) {
        self.persistence = persistence
        settings = ProximityReaderTrustSettings()
        loadTask = Task { [weak self, persistence] in
            do {
                let settings = try await Task.detached {
                    try persistence.loadEncodedSettings().map(ProximityReaderTrustSettingsCodec.decode)
                        ?? ProximityReaderTrustSettings()
                }.value
                self?.settings = settings
            } catch {
                self?.errorMessage = "Stored Reader Authentication settings were invalid and were not loaded: \(error.localizedDescription)"
            }
            self?.loading = false
        }
    }

    /// Read once; a pending load must never substitute the default reader policy.
    func sessionSnapshot() throws -> ProximityReaderTrustSettings {
        guard !loading else { throw WalletError.invalidInput("Reader Authentication settings are still loading") }
        return settings
    }

    func awaitPendingOperations() async {
        await loadTask?.value
        await saveTask?.value
    }

    func setReaderPolicy(_ policy: ProximityStoredReaderPolicy) {
        persist { $0.updatingReaderPolicy(policy) }
    }

    func prepareImport(sourceName: String, data: Data) async {
        guard !importInProgress, !loading else { return }
        importGeneration += 1
        let generation = importGeneration
        importInProgress = true
        pendingImport = nil
        errorMessage = nil
        defer { if generation == importGeneration { importInProgress = false } }
        do {
            let preview = try await ProximityReaderTrustSettingsCodec.prepareImport(
                sourceName: sourceName,
                data: data,
                existing: settings
            )
            try Task.checkCancellation()
            guard generation == importGeneration else { return }
            pendingImport = preview
        } catch is CancellationError {
            return
        } catch {
            if generation == importGeneration { errorMessage = error.localizedDescription }
        }
    }

    func confirmImport() {
        guard let pendingImport else { return }
        guard !importInProgress else { return }
        persist { _ in pendingImport.resultingSettings }
    }

    func cancelImport() {
        importGeneration += 1
        if importInProgress != (pendingWrites > 0) { importInProgress = pendingWrites > 0 }
        if pendingImport != nil { pendingImport = nil }
    }

    func removeReaderAuthority(id: String) {
        persist { $0.removingReaderTrustAnchor(id: id) }
    }

    func removeRICALProvider(id: String) {
        persist { $0.removingRICALProvider(id: id) }
    }

    func reset() {
        cancelImport()
        persist { _ in ProximityReaderTrustSettings() }
    }

    func dismissError() {
        if errorMessage != nil { errorMessage = nil }
    }

    func reportImportError(_ message: String) {
        importInProgress = pendingWrites > 0
        pendingImport = nil
        errorMessage = message
    }

    private func persist(_ update: @escaping (ProximityReaderTrustSettings) -> ProximityReaderTrustSettings) {
        cancelImport()
        pendingWrites += 1
        importInProgress = true
        let previous = saveTask
        saveTask = Task { [weak self, loadTask] in
            await loadTask?.value
            await previous?.value
            guard let self else { return }
            let newSettings = update(settings)
            do {
                let persistence = persistence
                try await Task.detached {
                    try persistence.saveEncodedSettings(ProximityReaderTrustSettingsCodec.encode(newSettings))
                }.value
                settings = newSettings
                pendingImport = nil
                errorMessage = nil
            } catch {
                errorMessage = "Reader Authentication settings could not be saved: \(error.localizedDescription)"
            }
            pendingWrites -= 1
            importInProgress = pendingWrites > 0
        }
    }
}
