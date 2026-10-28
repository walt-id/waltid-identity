import Combine
import SwiftUI
import UIKit
import WalletDemoSharingUI
import XCTest

@MainActor
final class WalletSheetSizingTests: XCTestCase {
    func testWithdrawnAutofocusCannotRunAfterTheResize() async throws {
        guard #available(iOS 16, *) else { throw XCTSkip("Custom sheet detents require iOS 16") }
        let model = SheetModel()
        let presentation = try SheetPresentation(model: model)
        defer { model.didFocus = nil; presentation.close() }
        let host = presentation.host
        let initialSize = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            host.view.bounds.height > 400 && host.view.bounds.height < 600
        }, object: nil)
        await fulfillment(of: [initialSize], timeout: 5)
        let notFocused = expectation(description: "Withdrawn focus request stays cancelled")
        notFocused.isInverted = true
        model.didFocus = { notFocused.fulfill() }
        let withdrawn = expectation(description: "Withdraw focus while UIKit lays out the compact sheet")
        host.didLayout = { [weak host] in
            guard let host, model.manual, host.view.bounds.height < 400 else { return }
            host.didLayout = nil
            // A close/submit decision must take effect before SwiftUI renders the next body.
            model.acceptsFocus = false
            withdrawn.fulfill()
        }
        model.manual = true
        await fulfillment(of: [withdrawn], timeout: 5)
        await fulfillment(of: [notFocused], timeout: 1)
    }

    func testAutofocusWaitsForTheManualSheetToFinishResizing() async throws {
        guard #available(iOS 16, *) else { throw XCTSkip("Custom sheet detents require iOS 16") }
        let model = SheetModel()
        let presentation = try SheetPresentation(model: model)
        defer { model.didFocus = nil; presentation.close() }
        let host = presentation.host
        let initialSize = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            host.view.bounds.height > 400 && host.view.bounds.height < 600
        }, object: nil)
        await fulfillment(of: [initialSize], timeout: 5)

        let focused = expectation(description: "Manual input automatically receives focus")
        let keyboardShown = expectation(description: "Manual input's keyboard finishes appearing")
        let context = SheetDetentContext(traits: host.traitCollection)
        var manualHeight: CGFloat?
        let keyboardObserver = NotificationCenter.default.addObserver(
            forName: UIResponder.keyboardDidShowNotification, object: nil, queue: .main
        ) { _ in keyboardShown.fulfill() }
        defer { NotificationCenter.default.removeObserver(keyboardObserver) }
        model.didFocus = {
            let layer = host.view.layer
            XCTAssertLessThan(layer.bounds.height, 400, "Resize the compact manual page before requesting its keyboard")
            let presentation = layer.presentation()
            XCTAssertNotNil(presentation, "Observe the rendered sheet, not only its model geometry")
            if let presentation {
                XCTAssertEqual(presentation.bounds.height, layer.bounds.height, accuracy: 1,
                    "Autofocus must not interrupt an unfinished sheet resize")
                XCTAssertEqual(presentation.position.y, layer.position.y, accuracy: 1,
                    "Autofocus must not compete with the sheet's position animation")
            }
            manualHeight = host.sheetPresentationController?.detents.first?.resolvedValue(in: context)
            focused.fulfill()
        }
        model.manual = true
        await fulfillment(of: [focused, keyboardShown], timeout: 5, enforceOrder: true)
        let heightBeforeKeyboard = try XCTUnwrap(manualHeight)
        let heightWithKeyboard = try XCTUnwrap(host.sheetPresentationController?.detents.first?.resolvedValue(in: context))
        XCTAssertEqual(heightWithKeyboard, heightBeforeKeyboard, accuracy: 1,
            "Keyboard avoidance must not become part of the content detent and start another sheet resize")
    }
}

@available(iOS 16, *)
private final class SheetDetentContext: NSObject, UISheetPresentationControllerDetentResolutionContext {
    let containerTraitCollection: UITraitCollection
    let maximumDetentValue: CGFloat = 2_000

    init(traits: UITraitCollection) { containerTraitCollection = traits }
}

@MainActor
private final class SheetModel: ObservableObject {
    @Published var manual = false
    var acceptsFocus = true
    var didFocus: (() -> Void)?
}

@MainActor
private final class SheetPresentation {
    let host: SheetHostingController
    private let presenter = UIViewController()
    private let window: UIWindow
    private let previousWindow: UIWindow?

    init(model: SheetModel) throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.first as? UIWindowScene)
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
    @FocusState private var focused: Bool
    @State private var input = ""

    var body: some View {
        WalletNavigationContainer {
            WalletReviewScaffold(showsActions: model.manual) {
                if model.manual {
                    TextField("Credential offer or request", text: $input)
                        .focused($focused).frame(height: 52)
                } else {
                    Rectangle().frame(height: 360)
                }
                Color.clear.frame(height: 16)
            } actions: {
                Button("Continue") {}.frame(height: 44)
            }
            .navigationTitle(model.manual ? "Enter a link" : "Scan QR code")
            .navigationBarTitleDisplayMode(.inline)
        }
        .ignoresSafeArea(.container, edges: .bottom)
        .walletSheetAutofocus($focused, when: model.manual && model.acceptsFocus)
        .onChange(of: focused) { if $0 { model.didFocus?() } }
        .walletSheetSizing()
    }
}
