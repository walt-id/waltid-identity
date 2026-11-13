import SwiftUI

/// Transient ownership of one reviewed attempt, never persisted as authorization.
@MainActor
final class ReviewSubmissionGate<Key: Equatable>: ObservableObject {
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
    @Published private(set) var changed = false
    private var current: Key?
    private var enabled = false
    private var consumed = false

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
    func close() { dismiss(); enabled = false; consumed = false; current = nil }
    private func deliver(_ attempt: Attempt) {
        guard enabled, !consumed, current == attempt.key else { return }
        consumed = true
        attempt.submit(attempt.revisions)
    }
}
