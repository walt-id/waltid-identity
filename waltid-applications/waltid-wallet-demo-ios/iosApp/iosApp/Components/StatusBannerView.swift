import SwiftUI
import WalletDemoSharingUI

struct StatusBannerView: View {
    let message: String
    let isLoading: Bool
    let isError: Bool
    var isExpanded: Bool = false
    var onDismiss: (() -> Void)? = nil
    var onToggleExpanded: (() -> Void)? = nil

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
                }
                .accessibilityIdentifier(WalletAccessibilityID.statusExpand)
            }
            if onDismiss != nil {
                Button(action: { onDismiss?() }) {
                    Image(systemName: "xmark")
                }
                .accessibilityIdentifier(WalletAccessibilityID.statusDismiss)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(minHeight: isError && !isExpanded ? 44 : nil, alignment: .top)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
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
        if isError { return Color.red.opacity(0.12) }
        if isLoading { return Color.secondary.opacity(0.12) }
        return Color.waltBlueContainer
    }

    private var foregroundColor: Color {
        if isError { return .red }
        if isLoading { return .secondary }
        return .waltBlueDark
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
