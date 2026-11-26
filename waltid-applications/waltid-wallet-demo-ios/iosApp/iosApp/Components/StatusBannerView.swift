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
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        WalletNoticeCard(tone: isError ? .error : .neutral, onDismiss: onDismiss) {
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
                        Image(systemName: "chevron.down")
                            .rotationEffect(.degrees(isExpanded ? 180 : 0))
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel(isExpanded ? "Collapse error" : "Expand error")
                    .accessibilityIdentifier(WalletAccessibilityID.statusExpand)
                }
                if let onDismiss {
                    WalletNoticeClose(label: String(localized: "Dismiss status"), action: onDismiss)
                        .accessibilityIdentifier(WalletAccessibilityID.statusDismiss)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(minHeight: isError && !isExpanded ? 44 : nil, alignment: .top)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
        }
        .animation(WalletMotion.disclosure(reduceMotion: reduceMotion), value: isExpanded)
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
}

enum WalletNoticeTone {
    case neutral, error, warning
}

struct WalletNoticeCard<Content: View>: View {
    let tone: WalletNoticeTone
    let onDismiss: (() -> Void)?
    let content: Content
    @Environment(\.colorScheme) private var colorScheme
    @GestureState private var dragOffset: CGFloat = 0

    init(tone: WalletNoticeTone, onDismiss: (() -> Void)?, @ViewBuilder content: () -> Content) {
        self.tone = tone
        self.onDismiss = onDismiss
        self.content = content()
    }

    var body: some View {
        let dark = colorScheme == .dark
        let warning = tone == .warning
        let background = warning ? color(dark ? 0x342A1B : 0xFDF0D5) : color(dark ? 0x2B2D33 : 0xE5E7ED)
        let foreground = switch tone {
            case .neutral: color(dark ? 0xC4C6D0 : 0x44474F)
            case .warning: color(dark ? 0xFFE5AE : 0x4B3208)
            case .error: color(dark ? 0xFFB4AB : 0xBA1A1A)
        }
        let shape = RoundedRectangle(cornerRadius: 12)
        content
            .foregroundStyle(foreground)
            .background(background, in: shape)
            .overlay {
                if warning { shape.strokeBorder(color(dark ? 0x8E754D : 0xC6A35C), lineWidth: 1).allowsHitTesting(false) }
            }
            .contentShape(Rectangle())
            .offset(x: dragOffset)
            .simultaneousGesture(
                DragGesture(minimumDistance: 20)
                    .updating($dragOffset) { value, offset, _ in
                        if abs(value.translation.width) > abs(value.translation.height) { offset = value.translation.width }
                    }
                    .onEnded { value in
                        if abs(value.translation.width) >= 80 && abs(value.translation.width) > abs(value.translation.height) {
                            onDismiss?()
                        }
                    },
                including: onDismiss == nil ? .none : .all
            )
    }

    private func color(_ rgb: UInt32) -> Color {
        Color(red: Double((rgb >> 16) & 0xff) / 255, green: Double((rgb >> 8) & 0xff) / 255,
            blue: Double(rgb & 0xff) / 255)
    }
}

struct WalletNoticeClose: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark").font(.callout.weight(.semibold))
                .frame(width: 44, height: 44).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(label))
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
