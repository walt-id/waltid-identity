import SwiftUI
import WalletSDK

public struct CredentialStatusIcon: View {
    let display: CredentialStatusDisplay
    let contrast: Bool
    @Environment(\.colorScheme) private var colorScheme
    public init(_ presentation: CredentialStatusPresentation, contrast: Bool = false) {
        self.display = .current(presentation); self.contrast = contrast
    }
    public init(_ display: CredentialStatusDisplay, contrast: Bool = false) {
        self.display = display; self.contrast = contrast
    }
    public var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 20, weight: .semibold))
            .foregroundStyle(color)
            .frame(width: 28, height: 28)
            .background(contrast ? Color(uiColor: .systemBackground) : .clear, in: Circle())
            .accessibilityHidden(true)
    }
    private var symbol: String {
        if display.presentation.map({ [.revoked, .invalid].contains($0.issue) }) == true { "xmark.circle.fill" }
        else if display.hasPositiveStatus { "checkmark.circle.fill" }
        else if display.hasStatusIssue { "exclamationmark.triangle.fill" }
        else { "info.circle" }
    }
    private var color: Color {
        if display.presentation.map({ [.revoked, .invalid].contains($0.issue) }) == true { .red }
        else if display.hasPositiveStatus {
            colorScheme == .dark ? Color(red: 0.51, green: 0.79, blue: 0.58) : Color(red: 0.13, green: 0.43, blue: 0.22)
        } else if display.hasStatusIssue {
            colorScheme == .dark ? Color(red: 1, green: 0.8, blue: 0.5) : Color(red: 0.58, green: 0.37, blue: 0)
        } else { .secondary }
    }
}
