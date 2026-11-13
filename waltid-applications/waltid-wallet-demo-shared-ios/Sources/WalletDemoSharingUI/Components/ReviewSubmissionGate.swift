import SwiftUI

/// Transient ownership of one reviewed attempt, never persisted as authorization.
@MainActor
public final class ReviewSubmissionGate<Key: Equatable>: ObservableObject {
    /// Creates an idle gate owned by one transient review host.
    public init() {}
    struct Attempt {
        let id = UUID()
        let key: Key
        let revisions: [String: String]
        let unsigned: Bool
        let message: String
        let affirmative: String
        let submit: ([String: String]) -> Void
    }
    @Published private(set) var pending: Attempt?
    @Published public private(set) var changed = false
    @Published private var current: Key?
    private var enabled = false
    @Published private var consumed = false

    /// Manual refresh cannot begin during a confirmation or after this attempt was consumed.
    public var canRefresh: Bool { pending == nil && !consumed && current != nil }

    func update(_ key: Key, enabled: Bool) {
        if current != key || self.enabled != enabled {
            if pending != nil { changed = true; pending = nil }
            consumed = false
        }
        current = key
        self.enabled = enabled
    }
    func begin(_ attempt: Attempt, needsConfirmation: Bool) {
        guard enabled, !consumed, current == attempt.key else { return }
        if changed { changed = false }
        if needsConfirmation { pending = attempt } else { deliver(attempt) }
    }
    func confirm(_ attempt: Attempt) {
        guard pending?.id == attempt.id else { return }
        pending = nil
        deliver(attempt)
    }
    func dismiss() { if pending != nil { pending = nil } }
    func close() { dismiss(); changed = false; enabled = false; consumed = false; current = nil }
    private func deliver(_ attempt: Attempt) {
        guard enabled, !consumed, current == attempt.key else { return }
        consumed = true
        attempt.submit(attempt.revisions)
    }
}
