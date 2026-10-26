import SwiftUI

/// Inline progress for list rows, review content and pinned feedback.
public struct WalletProgressRow: View {
    private let message: String

    public init(_ message: String) { self.message = message }

    public var body: some View {
        HStack(alignment: .center, spacing: 12) {
            ProgressView().fixedSize()
            Text(message).font(.callout).fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}
