import BackgroundTasks
import Foundation
import UIKit

/// The single app-owned refresh request. Both demo shells provide their own SDK entry point.
@MainActor
public final class WalletDemoBackgroundStatusScheduler {
    public enum Outcome: Sendable {
        case finished
        case finishedWithNotice
        case deferred
        case ineligible
    }

    public typealias Finish = @MainActor (Outcome) -> Void
    public typealias Start = @MainActor (String, @escaping Finish) -> (@Sendable () -> Void)
    public static let shared = WalletDemoBackgroundStatusScheduler(defaults: .standard) {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: $0)
    }

    private let identifier = (Bundle.main.bundleIdentifier ?? "") + ".credential-status-refresh"
    private let defaults: UserDefaults
    private let cancelRequest: (String) -> Void
    private let bindingKey = "wallet.credential-status.maintenance.binding"
    private var registered = false
    private var request: Task<Void, Never>?
    private var running: BackgroundStatusCompletion?

    init(defaults: UserDefaults, cancelRequest: @escaping (String) -> Void) {
        self.defaults = defaults
        self.cancelRequest = cancelRequest
    }

    public var canSchedule: Bool {
        registered && UIApplication.shared.backgroundRefreshStatus == .available
    }

    public func register(start: @escaping Start, onResult: @escaping @MainActor () -> Void) {
        registered = BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: nil) { [weak self] task in
            let completion = BackgroundStatusCompletion { task.setTaskCompleted(success: $0) }
            task.expirationHandler = { completion.finish(success: false, cancel: true) }
            Task { @MainActor in
                guard let self else { completion.finish(success: false); return }
                self.deliver(completion, start: start, onResult: onResult)
            }
        }
    }

    /// The wallet transaction has already committed the preference and retired any previous grant.
    public func reconcile(binding: String?) {
        let previous = defaults.string(forKey: bindingKey)
        defaults.set(binding, forKey: bindingKey)
        if previous != binding || binding == nil {
            request?.cancel()
            running?.finish(success: false, cancel: true)
            running = nil
            cancelRequest(identifier)
        }
        if binding != nil { WalletDemoCredentialStatusNotifications.shared.requestPermissionIfNeeded() }
        requestNext()
    }

    private func requestNext() {
        request?.cancel()
        guard canSchedule, let binding = defaults.string(forKey: bindingKey) else { return }
        request = Task { @MainActor [weak self] in
            guard let self else { return }
            let pending = await BGTaskScheduler.shared.pendingTaskRequests()
            guard !Task.isCancelled, self.canSchedule, self.defaults.string(forKey: self.bindingKey) == binding,
                  !pending.contains(where: { $0.identifier == self.identifier }) else { return }
            let request = BGAppRefreshTaskRequest(identifier: self.identifier)
            request.earliestBeginDate = Date(timeIntervalSinceNow: 3_600)
            // Submission can be refused by system policy. Foreground reconciliation retries it.
            try? BGTaskScheduler.shared.submit(request)
        }
    }

    func deliver(_ completion: BackgroundStatusCompletion, start: Start, onResult: @escaping @MainActor () -> Void) {
        guard let binding = defaults.string(forKey: bindingKey) else {
            completion.finish(success: true)
            return
        }
        requestNext()
        guard completion.isPending, running?.isPending != true else {
            completion.finish(success: false)
            return
        }
        running = completion
        let cancel = start(binding) { [weak self] outcome in
            let finish = {
                let success = outcome != .deferred
                guard let self, self.running === completion else {
                    completion.finish(success: success)
                    return
                }
                self.running = nil
                if outcome == .ineligible, self.defaults.string(forKey: self.bindingKey) == binding {
                    self.reconcile(binding: nil)
                }
                completion.finish(success: success)
                onResult()
            }
            if outcome == .finishedWithNotice {
                Task { @MainActor in
                    await WalletDemoCredentialStatusNotifications.shared.postIfAllowed {
                        guard let self else { return false }
                        return self.running === completion && completion.isPending &&
                            self.defaults.string(forKey: self.bindingKey) == binding
                    }
                    finish()
                }
            } else {
                finish()
            }
        }
        completion.install(cancel: cancel)
    }
}

/// Expiration may arrive before a job is published or race its success callback.
final class BackgroundStatusCompletion: @unchecked Sendable {
    private let lock = NSLock()
    private var acknowledge: ((Bool) -> Void)?
    private var cancellation: (@Sendable () -> Void)?
    private var expired = false

    init(acknowledge: @escaping (Bool) -> Void) { self.acknowledge = acknowledge }

    var isPending: Bool {
        lock.lock()
        defer { lock.unlock() }
        return acknowledge != nil
    }

    func install(cancel: @escaping @Sendable () -> Void) {
        lock.lock()
        let shouldCancel = expired
        if acknowledge != nil { cancellation = cancel }
        lock.unlock()
        if shouldCancel { cancel() }
    }

    func finish(success: Bool, cancel: Bool = false) {
        lock.lock()
        let finish = acknowledge
        let cancelWork = cancellation
        if finish != nil {
            acknowledge = nil
            cancellation = nil
            expired = cancel
        }
        lock.unlock()
        guard let finish else { return }
        if cancel { cancelWork?() }
        finish(success)
    }
}
