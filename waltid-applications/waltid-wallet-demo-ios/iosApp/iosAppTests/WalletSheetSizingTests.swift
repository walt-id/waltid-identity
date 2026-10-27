import Combine
import SwiftUI
import UIKit
import WalletDemoSharingUI
import XCTest

@MainActor
final class WalletSheetSizingTests: XCTestCase {
    func testAutofocusWaitsForTheManualSheetToFinishResizing() async throws {
        guard #available(iOS 16, *) else { throw XCTSkip("Custom sheet detents require iOS 16") }
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.first as? UIWindowScene)
        let previousWindow = scene.keyWindow
        let window = UIWindow(windowScene: scene)
        let presenter = UIViewController()
        window.rootViewController = presenter
        window.makeKeyAndVisible()
        defer {
            presenter.dismiss(animated: false)
            window.isHidden = true
            previousWindow?.makeKeyAndVisible()
        }

        let model = SheetModel()
        defer { model.didFocus = nil }
        let host = UIHostingController(rootView: SheetContent(model: model).walletSheetSizing())
        presenter.present(host, animated: false)
        let initialSize = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            host.view.bounds.height > 400 && host.view.bounds.height < 600
        }, object: nil)
        await fulfillment(of: [initialSize], timeout: 5)

        let focused = expectation(description: "Manual input automatically receives focus")
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
            focused.fulfill()
        }
        model.manual = true
        await fulfillment(of: [focused], timeout: 5)
    }
}

@MainActor
private final class SheetModel: ObservableObject {
    @Published var manual = false
    var didFocus: (() -> Void)?
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
        .walletSheetAutofocus($focused, when: model.manual)
        .onChange(of: focused) { if $0 { model.didFocus?() } }
    }
}
