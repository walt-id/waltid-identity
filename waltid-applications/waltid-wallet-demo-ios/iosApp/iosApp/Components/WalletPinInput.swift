import SwiftUI
import UIKit

/// One native secure editor owns input and accessibility. Decorative positions never reveal digits.
struct WalletPinInput: View {
    @Binding var value: String
    let label: String
    let digitCount: Int
    let isEnabled: Bool
    let isError: Bool
    let identifier: String
    let focus: Binding<Bool>
    let onSubmit: () -> Void

    var body: some View {
        PinSecureEditor(value: $value, focus: focus, label: label,
            isEnabled: isEnabled, identifier: identifier, onSubmit: onSubmit)
            .frame(maxWidth: .infinity).frame(height: 64)
            .privacySensitive()
            .overlay {
                GeometryReader { geometry in
                    let width = min(40, (geometry.size.width - CGFloat(digitCount - 1) * 16) / CGFloat(digitCount))
                    HStack(spacing: 16) {
                        ForEach(0..<digitCount, id: \.self) { index in
                            let active = focus.wrappedValue && isEnabled && index == value.count
                            VStack(spacing: 12) {
                                if index < value.count {
                                    Circle().fill(Color.primary).frame(width: 10, height: 10)
                                } else {
                                    Color.clear.frame(width: 10, height: 10)
                                }
                                Capsule().fill(isError ? Color.red : active ? Color.accentColor : Color.secondary.opacity(0.4))
                                    .frame(height: active || isError ? 3 : 2)
                            }
                            .frame(width: width, height: 44)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
    }
}

/// Retain focus intent until this editor's own window is ready, rather than racing SwiftUI focus.
private struct PinSecureEditor: UIViewRepresentable {
    @Binding var value: String
    @Binding var focus: Bool
    let label: String
    let isEnabled: Bool
    let identifier: String
    let onSubmit: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> TextField {
        let field = TextField()
        field.isSecureTextEntry = true
        field.keyboardType = .numberPad
        field.autocorrectionType = .no
        field.spellCheckingType = .no
        field.textColor = .clear
        field.tintColor = .clear
        field.delegate = context.coordinator
        field.addTarget(context.coordinator, action: #selector(Coordinator.changed(_:)), for: .editingChanged)
        context.coordinator.field = field
        let toolbar = UIToolbar()
        let done = UIButton(type: .system)
        done.setTitle("Done", for: .normal)
        done.titleLabel?.font = .preferredFont(forTextStyle: .body)
        done.titleLabel?.adjustsFontForContentSizeCategory = true
        done.accessibilityIdentifier = "wallet.pinKeyboardAction"
        done.addTarget(context.coordinator, action: #selector(Coordinator.dismissKeyboard), for: .touchUpInside)
        done.frame = CGRect(x: 0, y: 0, width: 64, height: 44)
        toolbar.items = [UIBarButtonItem(barButtonSystemItem: .flexibleSpace, target: nil, action: nil),
                         UIBarButtonItem(customView: done)]
        toolbar.sizeToFit()
        field.inputAccessoryView = toolbar
        return field
    }

    func updateUIView(_ field: TextField, context: Context) {
        context.coordinator.parent = self
        if field.text != value { field.text = value }
        field.isEnabled = isEnabled
        field.accessibilityLabel = label
        field.accessibilityHint = "\(value.count) digits entered"
        field.accessibilityIdentifier = identifier
        field.requestFocus(focus && isEnabled)
    }

    static func dismantleUIView(_ field: TextField, coordinator: Coordinator) {
        field.delegate = nil
        field.requestFocus(false)
        field.stopObservingWindow()
        field.resignFirstResponder()
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: PinSecureEditor
        weak var field: UITextField?
        init(_ parent: PinSecureEditor) { self.parent = parent }

        @objc func dismissKeyboard() {
            parent.focus = false
            field?.resignFirstResponder()
            parent.onSubmit()
        }

        @objc func changed(_ field: UITextField) {
            parent.value = field.text ?? ""
            // The shared input binding filters and caps paste as well as ordinary typing.
            if field.text != parent.value { field.text = parent.value }
        }

        func textFieldDidBeginEditing(_ textField: UITextField) { updateFocus(textField, focused: true) }
        func textFieldDidEndEditing(_ textField: UITextField) { updateFocus(textField, focused: false) }

        private func updateFocus(_ field: UITextField, focused: Bool) {
            guard parent.focus != focused else { return }
            // Disabling/replacing an editor can end editing inside a SwiftUI update.
            DispatchQueue.main.async { [weak self, weak field] in
                guard let self, let field, field.isFirstResponder == focused else { return }
                if parent.focus != focused { parent.focus = focused }
            }
        }
        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            parent.onSubmit()
            return true
        }
    }

    final class TextField: UITextField {
        private var wantsFocus = false
        private var observations: [NSObjectProtocol] = []

        func requestFocus(_ requested: Bool) {
            wantsFocus = requested
            // UIKit responder changes occur after SwiftUI has finished updating this editor.
            DispatchQueue.main.async { [weak self] in self?.applyFocus() }
        }

        override func didMoveToWindow() {
            super.didMoveToWindow()
            stopObservingWindow()
            guard let window else { return }
            let changes: [(Notification.Name, AnyObject?)] = [
                (UIWindow.didBecomeKeyNotification, window),
                (UIScene.didActivateNotification, window.windowScene),
            ]
            for (name, object) in changes {
                guard let object else { continue }
                observations.append(NotificationCenter.default.addObserver(forName: name, object: object, queue: .main) {
                    [weak self] _ in Task { @MainActor in self?.applyFocus() }
                })
            }
            requestFocus(wantsFocus)
        }

        func stopObservingWindow() {
            observations.forEach { NotificationCenter.default.removeObserver($0) }
            observations.removeAll()
        }

        private func applyFocus() {
            guard wantsFocus else {
                if isFirstResponder { resignFirstResponder() }
                return
            }
            guard isEnabled, let window, window.isKeyWindow,
                  window.windowScene?.activationState == .foregroundActive else { return }
            if !isFirstResponder { becomeFirstResponder() }
        }
    }
}
