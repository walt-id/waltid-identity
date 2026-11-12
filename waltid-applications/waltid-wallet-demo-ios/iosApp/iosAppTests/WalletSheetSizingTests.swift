import Combine
import SwiftUI
import UIKit
@testable import WalletDemoSharingUI
import XCTest
@testable import iosApp

@MainActor
final class WalletSheetSizingTests: XCTestCase {
    func testUnanimatedPresentationMismatchCanSettleWithoutAcceptingMotion() throws {
        let layer = SheetGeometryLayer()
        let rendered = SheetGeometryLayer()
        layer.rendered = rendered
        // Captured from CI after the sheet's resize completed: UIKit's container retained
        // a different presentation scale with no animation keys and no further movement.
        layer.transform = CATransform3DMakeScale(0.9601990049751243, 0.9601990049751243, 1)
        rendered.transform = CATransform3DMakeScale(0.9617239713035861, 0.9617239713035861, 1)
        let first = try XCTUnwrap(WalletSheetGeometrySnapshot(root: layer, container: layer))
        XCTAssertFalse(first.matches(nil), "A single sample cannot establish that the sheet stopped moving")
        XCTAssertTrue(WalletSheetGeometrySnapshot(root: layer, container: layer)?.matches(first) == true)

        rendered.position.y += 1
        XCTAssertFalse(WalletSheetGeometrySnapshot(root: layer, container: layer)?.matches(first) == true,
            "Unanimated presentation geometry must still stop changing before readiness")
        layer.keys = ["transform"]
        XCTAssertNil(WalletSheetGeometrySnapshot(root: layer, container: layer),
            "A real animation away from its target must finish before readiness")
        rendered.position = layer.position
        rendered.transform = layer.transform
        XCTAssertNotNil(WalletSheetGeometrySnapshot(root: layer, container: layer),
            "A retained key at the target must not block readiness")
    }

    func testCompletedContainerAnimationDoesNotKeepTheSheetUnready() async throws {
        guard #available(iOS 16, *) else { throw XCTSkip("Custom sheet detents require iOS 16") }
        let model = SheetModel()
        let presentation = try await SheetPresentation(model: model)
        defer { presentation.close() }
        let initial = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in model.layoutReady }, object: nil)
        await fulfillment(of: [initial], timeout: 5)

        let host = presentation.host
        let container = try XCTUnwrap(host.view.superview).layer
        let key = "retained-sheet-container-animation"
        defer { container.removeAnimation(forKey: key) }
        let settling = CABasicAnimation(keyPath: "opacity")
        settling.fromValue = 0.5
        settling.toValue = container.opacity
        settling.duration = 0.25
        // Core Animation may retain a completed animation for reuse. Its presence
        // does not mean the sheet is still moving or that its controls are unusable.
        settling.isRemovedOnCompletion = false
        settling.fillMode = .forwards
        container.add(settling, forKey: key)
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            model.layoutReady && host.view.bounds.height < 400
        }, object: nil)
        model.replacement = true

        let rendered = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            guard let content = host.view.layer.presentation(), let enclosing = container.presentation() else { return false }
            return host.view.bounds.height < 400
                && abs(content.bounds.height - host.view.bounds.height) < 1
                && abs(enclosing.position.y - container.position.y) < 1
                && abs(enclosing.opacity - container.opacity) < 0.001
        }, object: nil)
        await fulfillment(of: [rendered], timeout: 5)
        XCTAssertNotNil(container.animation(forKey: key), "Exercise a completed animation that remains attached")
        await fulfillment(of: [ready], timeout: 2)
    }

    func testSuccessDismissalAllowsFiveSecondsAfterTheSheetSettles() async throws {
        guard #available(iOS 16, *) else { throw XCTSkip("Custom sheet detents require iOS 16") }
        let model = SheetModel()
        let presentation = try await SheetPresentation(model: model)
        defer { model.didSettle = nil; model.didDismiss = nil; presentation.close() }
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in model.layoutReady }, object: nil)
        await fulfillment(of: [ready], timeout: 5)

        let dismissed = expectation(description: "Idle success dismisses after its readable interval")
        var settledAt: TimeInterval?
        model.didSettle = { settledAt = ProcessInfo.processInfo.systemUptime }
        model.didDismiss = {
            XCTAssertTrue(model.layoutReady, "A success must not disappear while its sheet is still resizing")
            XCTAssertNotNil(settledAt)
            if let settledAt {
                // Readiness notification and timer scheduling can occur on adjacent run-loop turns.
                XCTAssertGreaterThanOrEqual(ProcessInfo.processInfo.systemUptime - settledAt, 4.9,
                    "The five-second reading interval starts after the receipt becomes usable")
            }
            dismissed.fulfill()
        }
        let container = try XCTUnwrap(presentation.host.view.superview).layer
        let settling = CABasicAnimation(keyPath: "position.y")
        settling.fromValue = container.position.y + 40
        settling.toValue = container.position.y
        settling.duration = 6
        container.add(settling, forKey: "sheet-container-settling")
        model.replacement = true
        model.dismissSuccess = true
        await fulfillment(of: [dismissed], timeout: 15)
    }

    func testReplacingThePageWaitsForTheCurrentSheetResize() async throws {
        guard #available(iOS 16, *) else { throw XCTSkip("Custom sheet detents require iOS 16") }
        let model = SheetModel()
        let presentation = try await SheetPresentation(model: model)
        defer { presentation.host.didLayout = nil; model.didSettle = nil; presentation.close() }
        let host = presentation.host
        let initialSize = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            model.layoutReady && host.view.bounds.height > 400 && host.view.bounds.height < 600
        }, object: nil)
        await fulfillment(of: [initialSize], timeout: 5)

        let replaced = expectation(description: "Replacement page receives its live content height")
        let settled = expectation(description: "Replacement page becomes ready after its own resize")
        model.didSettle = {
            guard model.replacement else { return }
            let rendered = host.view.layer.presentation()
            XCTAssertNotNil(rendered)
            XCTAssertEqual(rendered?.bounds.height ?? 0, host.view.bounds.height, accuracy: 1,
                "Readiness must wait for the replacement page's resize, not the previous page's")
            XCTAssertEqual(rendered?.position.y ?? 0, host.view.layer.position.y, accuracy: 1)
            settled.fulfill()
        }
        host.didLayout = { [weak host] in
            guard let host, model.compact, host.view.bounds.height < 400 else { return }
            let firstTarget = host.view.bounds.height
            let rendered = host.view.layer.presentation()
            XCTAssertNotNil(rendered)
            XCTAssertGreaterThan(rendered?.bounds.height ?? 0, firstTarget + 1,
                "Replace the page during the real shrink animation, not after it")
            XCTAssertFalse(model.layoutReady)
            host.didLayout = { [weak host] in
                guard let host, model.replacement, host.view.bounds.height > firstTarget + 20 else { return }
                host.didLayout = nil
                let rendered = host.view.layer.presentation()
                XCTAssertNotNil(rendered)
                XCTAssertEqual(rendered?.bounds.height ?? 0, firstTarget, accuracy: 1,
                    "A new page must not interrupt the sheet's unfinished resize")
                XCTAssertFalse(model.layoutReady)
                replaced.fulfill()
            }
            // Let SwiftUI replace the measurement controller on its next update.
            DispatchQueue.main.async { model.replacement = true }
        }
        model.compact = true
        await fulfillment(of: [replaced, settled], timeout: 5, enforceOrder: true)
    }
}

@MainActor
private final class SheetModel: ObservableObject {
    @Published var compact = false
    @Published var replacement = false
    @Published var dismissSuccess = false
    var layoutReady = false
    var didSettle: (() -> Void)?
    var didDismiss: (() -> Void)?
}

@MainActor
private final class SheetPresentation {
    let host: SheetHostingController
    private let presenter = UIViewController()
    private let window: UIWindow
    private let previousWindow: UIWindow?

    init(model: SheetModel) async throws {
        // XCTest can enter the first test before SwiftUI connects the app's scene.
        // Present only after a real foreground window exists, rather than racing launch.
        func activeScene() -> UIWindowScene? {
            UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
                .first { $0.activationState == .foregroundActive && $0.keyWindow != nil }
        }
        let connected = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in activeScene() != nil }, object: nil)
        await XCTWaiter.fulfillment(of: [connected], timeout: 5)
        let scene = try XCTUnwrap(activeScene(), "The test app needs a foreground window before presenting a sheet")
        previousWindow = scene.keyWindow
        window = UIWindow(windowScene: scene)
        host = SheetHostingController(rootView: SheetContent(model: model))
        window.rootViewController = presenter
        window.makeKeyAndVisible()
        presenter.present(host, animated: false)
    }

    func close() {
        presenter.dismiss(animated: false)
        window.isHidden = true
        previousWindow?.makeKeyAndVisible()
    }
}

private final class SheetHostingController: UIHostingController<SheetContent> {
    var didLayout: (() -> Void)?

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        didLayout?()
    }
}

private struct SheetContent: View {
    @ObservedObject var model: SheetModel

    var body: some View {
        WalletNavigationContainer {
            WalletReviewScaffold(showsActions: model.compact) {
                if model.replacement {
                    Rectangle().frame(height: 180)
                } else if model.compact {
                    Rectangle().frame(height: 52)
                } else {
                    Rectangle().frame(height: 360)
                }
                Color.clear.frame(height: 16)
            } actions: {
                Button("Continue") {}.frame(height: 44)
            }
            .id(model.replacement)
            .background(SheetReadiness { ready in
                model.layoutReady = ready
                if ready { model.didSettle?() }
            })
            .walletSuccessDismissal(key: 0, enabled: model.dismissSuccess) { model.didDismiss?() }
            .environment(\.scenePhase, .active)
            .navigationTitle(model.compact ? "Compact review" : "Review")
            .navigationBarTitleDisplayMode(.inline)
        }
        .ignoresSafeArea(.container, edges: .bottom)
        .walletSheetSizing()
    }
}

private struct SheetReadiness: View {
    @Environment(\.walletSheetLayoutReady) private var ready
    let changed: (Bool) -> Void

    var body: some View {
        Color.clear.onAppear { changed(ready) }.onChange(of: ready, perform: changed)
    }
}

private final class SheetGeometryLayer: CALayer {
    var rendered: SheetGeometryLayer?
    var keys: [String] = []

    override func presentation() -> Self? { rendered as? Self }
    override func animationKeys() -> [String]? { keys }
}
