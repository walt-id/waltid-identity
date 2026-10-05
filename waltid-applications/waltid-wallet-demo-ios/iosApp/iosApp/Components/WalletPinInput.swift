import SwiftUI

/// One SecureField owns editing and accessibility. The circles never display the PIN itself.
struct WalletPinInput: View {
    @Binding var value: String
    let label: String
    let digitCount: Int
    let isEnabled: Bool
    let isError: Bool
    let identifier: String
    let focus: FocusState<Bool>.Binding
    let onSubmit: () -> Void

    var body: some View {
        SecureField("", text: $value)
            .keyboardType(.numberPad)
            .textContentType(nil)
            .autocorrectionDisabled()
            .foregroundStyle(.clear)
            .tint(.clear)
            .textFieldStyle(.plain)
            .frame(height: 64)
            .focused(focus)
            .disabled(!isEnabled)
            .onSubmit(onSubmit)
            .accessibilityLabel(label)
            .accessibilityHint("\(value.count) digits entered")
            .accessibilityIdentifier(identifier)
            .privacySensitive()
            .overlay {
                GeometryReader { geometry in
                    let diameter = min(44, (geometry.size.width - CGFloat(digitCount - 1) * 8) / CGFloat(digitCount))
                    HStack(spacing: 8) {
                        ForEach(0..<digitCount, id: \.self) { index in
                            let active = focus.wrappedValue && isEnabled && index == value.count
                            ZStack {
                                Circle().fill(active ? Color.accentColor.opacity(0.12) : Color(uiColor: .tertiarySystemFill))
                                if active || isError {
                                    Circle().strokeBorder(isError ? Color.red : Color.accentColor, lineWidth: 2)
                                }
                                if index < value.count {
                                    Circle().fill(Color.primary).frame(width: 12, height: 12)
                                } else {
                                    Circle().strokeBorder(Color.secondary, lineWidth: 1.5).frame(width: 8, height: 8)
                                }
                            }
                            .frame(width: diameter, height: diameter)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
    }
}
