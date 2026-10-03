import SwiftUI

/// The visible control stays compact while each button retains its own 44-point hit area.
public struct WalletCountControl: View {
    private let value: Binding<Int>
    private let range: ClosedRange<Int>
    private let enabled: Bool
    private let decreaseLabel: String
    private let increaseLabel: String
    private let identifier: String

    public init(value: Binding<Int>, range: ClosedRange<Int>, enabled: Bool,
                decreaseLabel: String, increaseLabel: String, identifier: String) {
        self.value = value
        self.range = range
        self.enabled = enabled
        self.decreaseLabel = decreaseLabel
        self.increaseLabel = increaseLabel
        self.identifier = identifier
    }

    public var body: some View {
        HStack(spacing: 0) {
            control("minus", label: decreaseLabel, suffix: "Decrement", available: value.wrappedValue > range.lowerBound) {
                value.wrappedValue -= 1
            }
            control("plus", label: increaseLabel, suffix: "Increment", available: value.wrappedValue < range.upperBound) {
                value.wrappedValue += 1
            }
        }
        .background {
            Capsule().fill(Color(.tertiarySystemFill)).frame(width: 76, height: 28)
                .overlay { Rectangle().fill(Color(.separator)).frame(width: 0.5, height: 14) }
        }
    }

    private func control(_ symbol: String, label: String, suffix: String, available: Bool,
                         action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 14, weight: .medium))
                .frame(width: 44, height: 44).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(enabled && available ? Color.primary : Color.secondary.opacity(0.4))
        .disabled(!enabled || !available)
        .accessibilityLabel(label)
        .accessibilityValue(String(value.wrappedValue))
        .accessibilityIdentifier("\(identifier)-\(suffix)")
    }
}
