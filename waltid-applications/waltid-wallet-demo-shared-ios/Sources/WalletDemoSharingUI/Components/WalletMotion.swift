import SwiftUI

/// Navigation uses an edge-to-edge journey; disclosures and other local changes stay restrained.
public enum WalletMotion {
    public static func navigation(reduceMotion: Bool) -> Animation? {
        reduceMotion ? nil : .spring(response: 0.38, dampingFraction: 0.92)
    }

    public static func page(forward: Bool, reduceMotion: Bool) -> AnyTransition {
        guard !reduceMotion else { return .identity }
        return .asymmetric(insertion: .move(edge: forward ? .trailing : .leading),
                           removal: .move(edge: forward ? .leading : .trailing))
    }
}
