import SwiftUI
import UIKit

extension View {
    /// Fit the active page, capped by the system's available presentation height.
    public func walletSheetSizing() -> some View { modifier(WalletSheetSizingModifier()) }

    /// Request the keyboard after the new page content has finished resizing its sheet.
    public func walletSheetAutofocus(_ focus: FocusState<Bool>.Binding, when requested: Bool) -> some View {
        modifier(WalletSheetAutofocusModifier(focus: focus, requested: requested))
    }
}

private struct WalletSheetAutofocusModifier: ViewModifier {
    let focus: FocusState<Bool>.Binding
    let requested: Bool
    @Environment(\.walletSheetSizing) private var sizing
    @State private var request: UUID?

    func body(content: Content) -> some View {
        content.onChange(of: requested) { requested in
            cancelRequest()
            guard requested else { focus.wrappedValue = false; return }
            guard #available(iOS 16, *), let sizing else { focus.wrappedValue = true; return }
            let id = UUID()
            request = id
            sizing.afterLayout(id: id) { [focus, request = $request] in
                guard request.wrappedValue == id else { return }
                request.wrappedValue = nil
                focus.wrappedValue = true
            }
        }.onDisappear(perform: cancelRequest)
    }

    private func cancelRequest() {
        if let request { sizing?.cancelAfterLayout(id: request) }
        request = nil
    }
}

private struct WalletSheetSizingModifier: ViewModifier {
    @StateObject private var sizing = WalletSheetSizingCoordinator()
    func body(content: Content) -> some View { content.environment(\.walletSheetSizing, sizing) }
}

private struct WalletSheetSizingKey: EnvironmentKey {
    static let defaultValue: WalletSheetSizingCoordinator? = nil
}

private extension EnvironmentValues {
    var walletSheetSizing: WalletSheetSizingCoordinator? {
        get { self[WalletSheetSizingKey.self] }
        set { self[WalletSheetSizingKey.self] = newValue }
    }
}

/// Measure the live content, including wrapping and Dynamic Type, without mounting a copy.
public struct WalletSheetScrollView<Content: View>: View {
    private let content: Content
    @Environment(\.walletSheetSizing) private var sizing
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(@ViewBuilder content: () -> Content) { self.content = content() }

    public var body: some View {
        ScrollView {
            VStack(spacing: 0) { content }
                .fixedSize(horizontal: false, vertical: true)
                .background(WalletSheetPageMeasurement(sizing: sizing, animated: !reduceMotion))
        }
    }
}

private final class WalletSheetSizingCoordinator: ObservableObject {
    weak var activePage: WalletSheetPageMeasurement.Controller?
    private weak var sheet: UISheetPresentationController?
    private var height: CGFloat = 0
    private let identifier = UISheetPresentationController.Detent.Identifier("wallet.content")
    private var resizing = false
    private var layoutCompletion: (id: UUID, action: () -> Void)?

    func afterLayout(id: UUID, _ completion: @escaping () -> Void) {
        layoutCompletion = (id, completion)
        activePage?.scheduleMeasurement()
    }

    func cancelAfterLayout(id: UUID) {
        if layoutCompletion?.id == id { layoutCompletion = nil }
    }

    func update(page: WalletSheetPageMeasurement.Controller, height: CGFloat, owner: UIViewController, animated: Bool) {
        guard #available(iOS 16, *), activePage === page, !resizing, height.isFinite, height > 0,
              let presentation = owner.sheetPresentationController else { return }
        let rounded = ceil(height)
        // SwiftUI can reset the same presentation controller when the task root changes.
        // Reinstall our detent after scanner/loading/review transitions, not only for a new host.
        let needsConfiguration = sheet !== presentation
            || presentation.detents.map(\.identifier) != [identifier]
        guard needsConfiguration || self.height != rounded else {
            // Layout can finish before the sheet's presentation does.
            if let transition = owner.transitionCoordinator, layoutCompletion != nil {
                transition.animate(alongsideTransition: nil) { [weak page] _ in page?.scheduleMeasurement() }
                return
            }
            let completion = layoutCompletion
            layoutCompletion = nil
            completion?.action()
            return
        }
        self.height = rounded
        let changes = { [self] in
            if needsConfiguration {
                self.sheet = presentation
                presentation.detents = [.custom(identifier: self.identifier) { [weak self] context in
                    min(self?.height ?? context.maximumDetentValue, context.maximumDetentValue)
                }]
                presentation.selectedDetentIdentifier = self.identifier
                presentation.prefersGrabberVisible = true
                presentation.prefersScrollingExpandsWhenScrolledToEdge = false
            } else { presentation.invalidateDetents() }
        }
        if animated && !owner.isBeingPresented {
            resizing = true
            CATransaction.begin()
            CATransaction.setCompletionBlock { [weak self] in
                DispatchQueue.main.async { [weak self] in
                    guard let self else { return }
                    resizing = false
                    // Re-measure the current page, not a height captured before another update.
                    activePage?.scheduleMeasurement()
                }
            }
            presentation.animateChanges(changes)
            CATransaction.commit()
        } else {
            changes()
            page.scheduleMeasurement()
        }
    }
}

/// UIKit's model geometry excludes presentation animation, unlike SwiftUI's resolved anchors.
/// Native appearance callbacks select the incoming page, including cancelled navigation.
private struct WalletSheetPageMeasurement: UIViewControllerRepresentable {
    let sizing: WalletSheetSizingCoordinator?
    let animated: Bool

    func makeUIViewController(context: Context) -> Controller { Controller() }
    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.sizing = sizing
        controller.animated = animated
        controller.scheduleMeasurement()
    }

    final class Controller: UIViewController {
        weak var sizing: WalletSheetSizingCoordinator?
        var animated = true
        private var measurementScheduled = false
        private var waitingForNavigation = false

        override func loadView() { view = UIView(); view.isUserInteractionEnabled = false }
        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            sizing?.activePage = self
            scheduleMeasurement()
        }
        override func viewWillDisappear(_ animated: Bool) {
            super.viewWillDisappear(animated)
            if sizing?.activePage === self { sizing?.activePage = nil }
        }
        override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); scheduleMeasurement() }

        func scheduleMeasurement() {
            guard !measurementScheduled else { return }
            measurementScheduled = true
            // Read after the containing navigation/scroll views finish this layout pass.
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                measurementScheduled = false
                measure()
            }
        }

        private func measure() {
            guard let sizing, sizing.activePage === self, view.window != nil else { return }
            if let transition = navigationController?.transitionCoordinator,
               transition.presentationStyle == .none {
                guard !waitingForNavigation else { return }
                waitingForNavigation = true
                // Native navigation holds the page's old viewport while it animates.
                // Resize after it releases that layout, so content and pinned actions
                // move with the sheet instead of jumping at the end of the push/pop.
                transition.animate(alongsideTransition: nil) { [weak self] _ in
                    self?.waitingForNavigation = false
                    self?.scheduleMeasurement()
                }
                return
            }
            var ancestor = view.superview
            while ancestor != nil && !(ancestor is UIScrollView) { ancestor = ancestor?.superview }
            guard let scroll = ancestor as? UIScrollView else { return }
            // The navigation page can retain its previous height during a native push.
            // Measure its chrome in that page's coordinates, not the resizing sheet's.
            var page: UIViewController = self
            while let parent = page.parent, !(parent is UINavigationController) { page = parent }
            var owner = page
            while let parent = owner.parent { owner = parent }
            guard owner.presentingViewController != nil else { return }
            // A focus request can queue a measurement before SwiftUI has laid out the
            // replacement content. Flush the owning hierarchy before reading its geometry.
            owner.view.layoutIfNeeded()
            guard sizing.activePage === self, view.window != nil else { return }
            let viewport = scroll.convert(scroll.bounds, to: page.view)
            let insets = scroll.adjustedContentInset
            // Include navigation chrome, outer padding, and pinned actions. UIKit adds the
            // bottom safe area to custom detents itself, so exclude it from our target.
            let chrome = max(0, viewport.minY) + max(0, page.view.bounds.height - viewport.maxY)
                + insets.top + insets.bottom - page.view.safeAreaInsets.bottom
            sizing.update(page: self, height: view.bounds.height + chrome, owner: owner, animated: animated)
        }
    }
}
