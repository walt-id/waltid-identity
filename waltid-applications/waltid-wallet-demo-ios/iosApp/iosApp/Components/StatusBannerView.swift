import SwiftUI
import UIKit
import WalletDemoSharingUI

struct StatusBannerView: View {
    let message: String
    let isLoading: Bool
    let isError: Bool
    var isExpanded: Bool = false
    var onDismiss: (() -> Void)? = nil
    var onToggleExpanded: (() -> Void)? = nil
    @Environment(\.walletDemoBranding) private var branding

    @State private var dragOffset: CGFloat = 0

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            if isLoading {
                ProgressView()
                    .controlSize(.small)
            }
            Text(message)
                .font(.subheadline)
                .lineLimit(isError && !isExpanded ? 2 : nil)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier(WalletAccessibilityID.status)
            if isError {
                Button(action: { onToggleExpanded?() }) {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .frame(minWidth: 44, minHeight: 44)
                }
                .accessibilityLabel(isExpanded ? "Collapse error" : "Expand error")
                .accessibilityIdentifier(WalletAccessibilityID.statusExpand)
            }
            if onDismiss != nil {
                Button(action: { onDismiss?() }) {
                    Image(systemName: "xmark")
                        .frame(minWidth: 44, minHeight: 44)
                }
                .accessibilityLabel("Dismiss status")
                .accessibilityIdentifier(WalletAccessibilityID.statusDismiss)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(minHeight: isError && !isExpanded ? 44 : nil, alignment: .top)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(backgroundColor)
        .foregroundColor(foregroundColor)
        .cornerRadius(8)
        .offset(x: dragOffset)
        .gesture(dismissGesture)
        .onTapGesture {
            if isError {
                onToggleExpanded?()
            }
        }
        .onChange(of: message) { message in
            guard UIAccessibility.isVoiceOverRunning else { return }
            UIAccessibility.post(notification: .announcement, argument: NSAttributedString(string: message,
                attributes: [.accessibilitySpeechQueueAnnouncement: true]))
        }
    }

    private var dismissGesture: some Gesture {
        DragGesture(minimumDistance: 20)
            .onChanged { value in
                guard onDismiss != nil else { return }
                dragOffset = value.translation.width
            }
            .onEnded { value in
                guard onDismiss != nil else {
                    dragOffset = 0
                    return
                }
                if abs(value.translation.width) > 80 {
                    onDismiss?()
                }
                dragOffset = 0
            }
    }

    private var backgroundColor: Color {
        return Color.secondary.opacity(0.08)
    }

    private var foregroundColor: Color {
        if isError { return .red }
        return .secondary
    }
}

struct WarningBannerView: View {
    let message: String

    var body: some View {
        Text(message)
            .font(.subheadline)
            .lineLimit(3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color.orange.opacity(0.16))
            .foregroundColor(.orange)
            .cornerRadius(8)
            .accessibilityIdentifier(WalletAccessibilityID.transactionDataProfilesWarning)
    }
}
