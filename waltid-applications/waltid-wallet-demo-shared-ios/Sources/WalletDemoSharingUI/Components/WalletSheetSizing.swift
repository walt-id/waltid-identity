import SwiftUI
import UIKit

extension View {
    /// Fit the active page, capped by the system's available presentation height.
    public func walletSheetSizing() -> some View { modifier(WalletSheetSizingModifier()) }

    /// Request the keyboard after the new page content has finished resizing its sheet.
    public func walletSheetAutofocus(_ focus: FocusState<Bool>.Binding,
        when requested: @autoclosure @escaping () -> Bool) -> some View {
        modifier(WalletSheetAutofocusModifier(focus: focus, isRequested: requested))
    }
}

private struct WalletSheetAutofocusModifier: ViewModifier {
    let focus: FocusState<Bool>.Binding
    let isRequested: () -> Bool
    @Environment(\.walletSheetSizing) private var sizing
    @State private var request: UUID?

    func body(content: Content) -> some View {
        content.onChange(of: isRequested()) { requested in
            cancelRequest()
            guard requested else { focus.wrappedValue = false; return }
            guard #available(iOS 16, *), let sizing else { focus.wrappedValue = true; return }
            let id = UUID()
            request = id
            sizing.afterLayout(id: id) { [focus, request = $request, isRequested] in
                guard request.wrappedValue == id else { return }
                request.wrappedValue = nil
                // Closing or submitting can precede SwiftUI's next onChange/onDisappear.
                guard isRequested() else { return }
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
    func body(content: Content) -> some View {
        content.environment(\.walletSheetSizing, sizing)
            .environment(\.walletSheetLayoutReady, layoutReady)
    }

    private var layoutReady: Bool {
        if #available(iOS 16, *) { return sizing.layoutReady }
        return true
    }
}

private struct WalletSheetSizingKey: EnvironmentKey {
    static let defaultValue: WalletSheetSizingCoordinator? = nil
}

private struct WalletSheetLayoutReadyKey: EnvironmentKey {
    static let defaultValue = true
}

extension EnvironmentValues {
    /// The current page has been measured after its sheet and navigation animations finished.
    public internal(set) var walletSheetLayoutReady: Bool {
        get { self[WalletSheetLayoutReadyKey.self] }
        set { self[WalletSheetLayoutReadyKey.self] = newValue }
    }
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
    weak var activePage: WalletSheetPageMeasurement.Controller? {
        didSet {
            guard activePage !== oldValue else { return }
            // Appearance callbacks can run inside a SwiftUI update. Invalidate before
            // the queued measurement, without publishing from that update itself.
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                if layoutReady { layoutReady = false }
                activePage?.scheduleMeasurement()
            }
        }
    }
    @Published private(set) var layoutReady = false
    private weak var sheet: UISheetPresentationController?
    private var height: CGFloat = 0
    private let identifier = UISheetPresentationController.Detent.Identifier("wallet.content")
    private weak var resizingSheet: UISheetPresentationController?
    private var resizeLink: CADisplayLink?
    private var layoutCompletion: (id: UUID, action: () -> Void)?

    deinit { resizeLink?.invalidate() }

    func afterLayout(id: UUID, _ completion: @escaping () -> Void) {
        layoutCompletion = (id, completion)
        activePage?.scheduleMeasurement()
    }

    func cancelAfterLayout(id: UUID) {
        if layoutCompletion?.id == id { layoutCompletion = nil }
    }

    func isResizing(_ owner: UIViewController) -> Bool {
        resizeLink != nil && resizingSheet === owner.sheetPresentationController
    }

    func geometryIsSettled(_ owner: UIViewController) -> Bool {
        // UIKit also animates containers above the content host. Its bounds can settle
        // before those containers finish moving or scaling, so focus must wait for both.
        // Observe only the sheet's ancestor chain, excluding content such as loading spinners.
        let container = owner.sheetPresentationController?.containerView?.layer
        var layer: CALayer? = owner.view.layer
        while let current = layer {
            guard let rendered = current.presentation() else { return false }
            // Animation keys can outlive their animation. Compare visible geometry instead,
            // allowing only numerical round-off in Core Animation's presentation copy.
            guard geometry(current).elementsEqual(geometry(rendered), by: { abs($0 - $1) < 0.000001 }) else { return false }
            if current === container { break }
            layer = current.superlayer
        }
        return true
    }

    private func geometry(_ layer: CALayer) -> [CGFloat] {
        let bounds = layer.bounds, position = layer.position, transform = layer.transform
        return [bounds.minX, bounds.minY, bounds.width, bounds.height, position.x, position.y,
                transform.m11, transform.m12, transform.m13, transform.m14,
                transform.m21, transform.m22, transform.m23, transform.m24,
                transform.m31, transform.m32, transform.m33, transform.m34,
                transform.m41, transform.m42, transform.m43, transform.m44]
    }

    func update(page: WalletSheetPageMeasurement.Controller, height: CGFloat, owner: UIViewController, animated: Bool) {
        guard #available(iOS 16, *), activePage === page,
              !isResizing(owner), height.isFinite, height > 0,
              let presentation = owner.sheetPresentationController else { return }
        let rounded = ceil(height)
        // SwiftUI can reset the same presentation controller when the task root changes.
        // Reinstall our detent after scanner/loading/review transitions, not only for a new host.
        let needsConfiguration = sheet !== presentation
            || presentation.detents.map(\.identifier) != [identifier]
        guard needsConfiguration || self.height != rounded else {
            // Layout can finish before the sheet's presentation does.
            if let transition = owner.transitionCoordinator {
                transition.animate(alongsideTransition: nil) { [weak page] _ in page?.scheduleMeasurement() }
                return
            }
            if !layoutReady { layoutReady = true }
            let completion = layoutCompletion
            layoutCompletion = nil
            completion?.action()
            return
        }
        if layoutReady { layoutReady = false }
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
            resizeLink?.invalidate()
            let observer = WalletSheetResizeObserver(sizing: self, owner: owner)
            let link = CADisplayLink(target: observer, selector: #selector(WalletSheetResizeObserver.frame(_:)))
            resizeLink = link
            resizingSheet = presentation
            presentation.animateChanges(changes)
            // Observe this sheet, not a transaction that can also contain a loading spinner.
            link.add(to: .main, forMode: .common)
        } else {
            changes()
            page.scheduleMeasurement()
        }
    }

    func finishResize(_ link: CADisplayLink) {
        link.invalidate()
        guard resizeLink === link else { return }
        resizeLink = nil
        resizingSheet = nil
        activePage?.scheduleMeasurement()
    }
}

private final class WalletSheetResizeObserver: NSObject {
    private weak var sizing: WalletSheetSizingCoordinator?
    private weak var owner: UIViewController?
    private var settledOnPreviousFrame = false

    init(sizing: WalletSheetSizingCoordinator, owner: UIViewController) {
        self.sizing = sizing
        self.owner = owner
    }

    @objc func frame(_ link: CADisplayLink) {
        guard let sizing else { link.invalidate(); return }
        if let owner, owner.view.window != nil, owner.presentingViewController != nil {
            let settled = sizing.geometryIsSettled(owner)
            let finished = settled && settledOnPreviousFrame
            settledOnPreviousFrame = settled
            // Keep measurements out of the resize until the rendered target has survived
            // a display boundary. A replacement page then measures its latest content once.
            guard finished else { return }
        }
        sizing.finishResize(link)
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
            // Even a measurement can mutate the sheet: layoutIfNeeded below flushes
            // the entire hosting hierarchy. Leave an active resize alone, including
            // when its measurement controller has been replaced. The resize observer
            // schedules the currently visible page with the latest content.
            guard !sizing.isResizing(owner) else { return }
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
