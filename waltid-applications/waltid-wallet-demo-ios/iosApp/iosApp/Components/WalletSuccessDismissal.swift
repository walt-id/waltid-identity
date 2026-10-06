import SwiftUI
import UIKit

/// A success stays visible after interaction, with VoiceOver, or while the app is inactive.
private struct WalletSuccessDismissal: ViewModifier {
    let key: Int
    let enabled: Bool
    let onDone: () -> Void
    @Environment(\.scenePhase) private var scenePhase
    @State private var interacted = false
    @State private var voiceOver = UIAccessibility.isVoiceOverRunning

    private struct TimerKey: Equatable {
        let operation: Int
        let eligible: Bool
    }

    func body(content: Content) -> some View {
        content
            .simultaneousGesture(DragGesture(minimumDistance: 0).onChanged { _ in interacted = true })
            .accessibilityElement(children: .contain)
            .onChange(of: key) { _ in interacted = false }
            .onReceive(NotificationCenter.default.publisher(for: UIAccessibility.voiceOverStatusDidChangeNotification)) { _ in
                voiceOver = UIAccessibility.isVoiceOverRunning
            }
            .task(id: TimerKey(operation: key, eligible: enabled && !interacted && !voiceOver && scenePhase == .active)) {
                guard enabled && !interacted && !voiceOver && scenePhase == .active else { return }
                do { try await Task.sleep(nanoseconds: 5_000_000_000) } catch { return }
                guard !Task.isCancelled else { return }
                onDone()
            }
    }
}

extension View {
    func walletSuccessDismissal(key: Int, enabled: Bool, onDone: @escaping () -> Void) -> some View {
        modifier(WalletSuccessDismissal(key: key, enabled: enabled, onDone: onDone))
    }
}
