import SwiftUI
import WalletSDK

public struct CredentialStatusIcon: View {
    let display: CredentialStatusDisplay
    let contrast: Bool
    let size: CGFloat
    @Environment(\.colorScheme) private var colorScheme
    public init(_ presentation: CredentialStatusPresentation, contrast: Bool = false, size: CGFloat = 28) {
        self.display = .current(presentation); self.contrast = contrast; self.size = size
    }
    public init(_ display: CredentialStatusDisplay, contrast: Bool = false, size: CGFloat = 28) {
        self.display = display; self.contrast = contrast; self.size = size
    }
    public var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 20 / 28, weight: .semibold))
            .foregroundStyle(color)
            .offset(y: contrast && isWarning ? -size * 2 / 28 : 0)
            .frame(width: size, height: size)
            .background(contrast ? Color(uiColor: .systemBackground) : .clear, in: Circle())
            .accessibilityHidden(true)
    }
    private var isAdverse: Bool {
        display.presentation.map { [.revoked, .invalid].contains($0.issue) } == true
    }
    private var isWarning: Bool { display.hasStatusIssue && !isAdverse && !display.hasPositiveStatus }
    private var symbol: String {
        if isAdverse { "xmark.circle.fill" }
        else if display.hasPositiveStatus { "checkmark.circle.fill" }
        else if display.hasStatusIssue { "exclamationmark.triangle.fill" }
        else { "info.circle" }
    }
    private var color: Color {
        if isAdverse { .red }
        else if display.hasPositiveStatus {
            colorScheme == .dark ? Color(red: 0.51, green: 0.79, blue: 0.58) : Color(red: 0.13, green: 0.43, blue: 0.22)
        } else if display.hasStatusIssue {
            colorScheme == .dark ? Color(red: 1, green: 0.8, blue: 0.5) : Color(red: 0.58, green: 0.37, blue: 0)
        } else { .secondary }
    }
}
