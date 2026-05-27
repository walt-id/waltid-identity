import SwiftUI

struct StatusBannerView: View {
    let message: String
    let isLoading: Bool
    let isError: Bool

    var body: some View {
        HStack(spacing: 8) {
            if isLoading {
                ProgressView()
                    .controlSize(.small)
            }
            Text(message)
                .font(.subheadline)
                .lineLimit(2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(backgroundColor)
        .foregroundColor(foregroundColor)
        .cornerRadius(8)
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
