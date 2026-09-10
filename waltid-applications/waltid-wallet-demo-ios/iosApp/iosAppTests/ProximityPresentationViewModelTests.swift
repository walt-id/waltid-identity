import Foundation
import SwiftUI
import UIKit
import XCTest
import WalletDemoSharingUI
import ZXingCpp
@testable import iosApp
import WalletDemoIdentityDocumentSupport
@testable import WalletSDK
@preconcurrency import WalletCore

final class ProximityPresentationViewModelTests: XCTestCase {
    func testSessionConfigurationMatrixRoundTripsThroughKotlinBridge() {
        let ble = WalletSDK.ProximityBLEConfiguration(roles: .centralClient, bearerPolicy: .gattOnly)
        let plans: [WalletSDK.ProximityRetrievalOptions] = [
            .init(bluetoothLowEnergy: ble),
            .init(bluetoothLowEnergy: nil, nfc: .init(maximumCommandDataLength: 255, maximumResponseDataLength: 256)),
            .init(bluetoothLowEnergy: ble, nfc: .init(maximumCommandDataLength: 255, maximumResponseDataLength: 256)),
        ]
        var sessions = plans.map(WalletSDK.ProximitySessionConfiguration.qr)
        for handover in [WalletSDK.ProximityNFCHandover.staticHandover, .negotiatedHandover] {
            for retrieval in plans {
                for qr in [nil] + plans.map(Optional.some) {
                    sessions.append(.nfc(.init(handover: handover, retrieval: retrieval, qrFallback: qr)))
                }
            }
        }
        for limit in [1, 65_536] {
            for qr in [nil] + plans.map(Optional.some) {
                sessions.append(.provisionalNFCV2(.init(
                    maximumCommandDataLength: limit, bluetoothLowEnergy: ble, qrFallback: qr
                )))
            }
        }
        for profile in WalletSDK.ProximityProfile.allCases {
            for session in sessions where profile != .iso1801352021 || !session.usesProvisionalNFCV2 {
                let configuration = WalletSDK.ProximityConfiguration(
                    profile: profile, session: session,
                    readerPolicy: profile == .eudiARF3FCAF202608 ? .requireTrusted : .allowAnonymousOrUntrusted
                )
                XCTAssertEqual(swiftSession(configuration.toKMPConfiguration().session), session)
            }
        }
    }

    func testLifecyclePolicyPreservesTransientInactiveStateAndInterruptsOnBackground() {
        XCTAssertFalse(ProximityPresentationLifecyclePolicy.shouldInterrupt(for: .active))
        XCTAssertFalse(ProximityPresentationLifecyclePolicy.shouldInterrupt(for: .inactive))
        XCTAssertTrue(ProximityPresentationLifecyclePolicy.shouldInterrupt(for: .background))

    }

    @MainActor
    func testStartExplainsBluetoothAndWaitsForExplicitActionBeforeCreatingSession() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(
            session: session,
            capabilityResults: [
                makeProximityCapabilities(
                    bluetoothAvailable: false,
                    nfcAvailable: false,
                    bluetoothRemediation: [.requestBluetoothPermission]
                ),
                makeProximityCapabilities(),
            ]
        )
        let hostActions = FakeProximityHostActionExecutor()
        let viewModel = ProximityPresentationViewModel(client: client, hostActions: hostActions)

        viewModel.start()
        try await waitUntil { client.capabilityCallCount == 1 }
        XCTAssertTrue(hostActions.actions.isEmpty)
        XCTAssertEqual(client.startCount, 0)
        viewModel.remediate(.requestBluetoothPermission)
        try await waitUntil {
            client.capabilityCallCount == 2 && client.startCount == 1
        }

        XCTAssertEqual(hostActions.actions, [.requestBluetoothPermission])
        XCTAssertEqual(client.startCount, 1)
    }

    @MainActor
    func testStartRequiresRemediationWhenEveryRetrievalRouteIsUnavailable() async throws {
        let session = FakeProximitySession()
        let capabilities = makeProximityCapabilities(
            bluetoothAvailable: false,
            nfcAvailable: false,
            bluetoothRemediation: [.openApplicationSettings]
        )
        let client = FakeProximityWalletClient(
            session: session,
            capabilityResults: [capabilities]
        )
        let viewModel = ProximityPresentationViewModel(
            client: client,
            hostActions: FakeProximityHostActionExecutor()
        )

        viewModel.start()
        try await waitUntil { client.capabilityCallCount == 1 }

        XCTAssertEqual(client.startCount, 0)
        XCTAssertEqual(viewModel.sessionState, .checkingPrerequisites(capabilities))
    }

    @MainActor
    func testSelectedBluetoothPermissionIsOfferedOnceEvenWhenNfcCanStart() async throws {
        for capabilities in [
            makeProximityCapabilities(
                bluetoothAvailable: false,
                bluetoothRemediation: [.requestBluetoothPermission]
            ),
            makeProximityCapabilities(nfcAvailable: false),
        ] {
            let session = FakeProximitySession()
            let client = FakeProximityWalletClient(session: session, capabilityResults: [capabilities])
            let hostActions = FakeProximityHostActionExecutor()
            let viewModel = ProximityPresentationViewModel(client: client, hostActions: hostActions)
            viewModel.start()
            try await waitUntil { client.capabilityCallCount == 1 }
            if capabilities.remediationActions.contains(.requestBluetoothPermission) {
                XCTAssertTrue(hostActions.actions.isEmpty)
                viewModel.remediate(.requestBluetoothPermission)
            }
            try await waitUntil { client.startCount == 1 }
            XCTAssertEqual(hostActions.actions, capabilities.remediationActions.contains(.requestBluetoothPermission)
                ? [.requestBluetoothPermission] : [])
            viewModel.dismiss()
            try await waitUntilAsync { await session.closeCount == 1 }
        }
    }

    @MainActor
    func testNfcSystemPresentationBackgroundDoesNotCancelActiveExchange() async throws {
        let session = FakeProximitySession()
        session.presentment.setActive(true)
        let client = FakeProximityWalletClient(session: session)
        let viewModel = ProximityPresentationViewModel(
            client: client,
            hostActions: FakeProximityHostActionExecutor()
        )

        viewModel.start()
        await session.emit(.preparing(profile: .iso180135Edition2DIS2026))
        await session.emit(.engagementReady([.qr(payload: "mdoc:device-engagement")]))
        try await waitUntil { viewModel.qrPayload == "mdoc:device-engagement" }

        XCTAssertEqual(client.startCount, 1)
        let configuration = try XCTUnwrap(client.lastConfiguration)
        guard case let .nfc(nfc) = configuration.session, nfc.handover == .negotiatedHandover, nfc.qrFallback != nil else {
            return XCTFail("The demo must prepare QR and NFC Negotiated Handover")
        }
        guard nfc.retrieval.bluetoothLowEnergy != nil,
              nfc.retrieval.nfc != nil else {
            return XCTFail("The demo must prepare BLE and conventional NFC retrieval")
        }
        XCTAssertTrue(viewModel.active)

        viewModel.handleLifecycleInterruption()
        let actions = await session.actions
        XCTAssertEqual(actions, [])

        viewModel.dismiss()
        try await waitUntilAsync { await session.closeCount == 1 }
    }

    @MainActor
    func testConfiguredNfcCancelsOnBackgroundOutsideSystemPresentment() async throws {
        for state in [
            WalletSDK.ProximityState.preparing(profile: .iso180135Edition2DIS2026),
            .engagementReady([.nfc]),
            .awaitingRequest(exchange: 1),
            .reviewRequired(combinedProximityReview()),
        ] {
            let session = FakeProximitySession()
            let client = FakeProximityWalletClient(session: session)
            let viewModel = ProximityPresentationViewModel(
                client: client,
                hostActions: FakeProximityHostActionExecutor()
            )
            viewModel.start()
            await session.emit(state)
            try await waitUntil { viewModel.sessionState == state }
            session.presentment.setActive(true)
            viewModel.handleLifecycleInterruption()
            let actions = await session.actions
            XCTAssertEqual(actions, [])
            session.presentment.setActive(false)
            viewModel.handleLifecycleInterruption()
            try await waitUntilAsync { await session.actions == [.cancel] }
            viewModel.dismiss()
            try await waitUntilAsync { await session.closeCount == 1 }
        }
    }

    @MainActor
    func testQrOnlyLifecycleBackgroundCancelsActiveExchange() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        let viewModel = ProximityPresentationViewModel(
            client: client,
            configurationProvider: { WalletSDK.ProximityConfiguration() },
            hostActions: FakeProximityHostActionExecutor()
        )

        viewModel.start()
        await session.emit(.preparing(profile: .iso180135Edition2DIS2026))
        await session.emit(.engagementReady([.qr(payload: "mdoc:device-engagement")]))
        try await waitUntil { viewModel.qrPayload == "mdoc:device-engagement" }

        viewModel.handleLifecycleInterruption()
        try await waitUntilAsync { await session.actions == [.cancel] }

        await session.emit(.cancelled)
        try await waitUntil { viewModel.isTerminal }
        XCTAssertEqual(viewModel.sessionState, .cancelled)
    }

    @MainActor
    func testStartupCancellationClosesSessionReturnedByLateStartWithoutRestoringState() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session, suspendStart: true)
        let viewModel = ProximityPresentationViewModel(
            client: client,
            hostActions: FakeProximityHostActionExecutor()
        )

        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        viewModel.cancel()
        client.resumeStart()

        try await waitUntilAsync { await session.closeCount == 1 }
        XCTAssertFalse(viewModel.active)
        XCTAssertNil(viewModel.sessionState)
        XCTAssertFalse(viewModel.startupFailed)
    }

    @MainActor
    func testCombinedReviewDefaultsAndApprovalPreserveExactHolderSelections() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        let viewModel = ProximityPresentationViewModel(
            client: client,
            hostActions: FakeProximityHostActionExecutor()
        )
        let review = combinedProximityReview()
        let familyName = WalletSDK.ProximityElementReference(
            namespace: "org.iso.18013.5.1",
            elementIdentifier: "family_name"
        )

        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        await session.emit(.reviewRequired(review))
        try await waitUntil { viewModel.review == review }

        XCTAssertEqual(viewModel.selections.count, 1)
        XCTAssertNil(viewModel.selections.first(where: { $0.requestIndex == 0 }))
        XCTAssertFalse(viewModel.canApprove)

        viewModel.selectCredential(requestIndex: 0, credentialID: "payment-b")
        XCTAssertEqual(
            viewModel.selections.first(where: { $0.requestIndex == 0 })?.credentialID,
            "payment-b"
        )
        viewModel.toggleElement(requestIndex: 0, element: familyName)
        XCTAssertFalse(viewModel.canApprove)
        viewModel.toggleElement(requestIndex: 0, element: familyName)
        XCTAssertTrue(viewModel.canApprove)

        viewModel.setContinueAfterResponse(true)
        viewModel.approve()
        try await waitUntilAsync { await session.actions.count == 1 }
        let actions = await session.actions
        guard case .approve(let reviewID, let submission) = try XCTUnwrap(actions.first) else {
            return XCTFail("Expected an approval action")
        }
        XCTAssertEqual(reviewID, review.reviewID)
        XCTAssertEqual(submission.documents.count, 2)
        XCTAssertTrue(submission.continueAfterResponse)
        XCTAssertEqual(
            submission.documents.first(where: { $0.requestIndex == 0 })?.credentialID,
            "payment-b"
        )
        XCTAssertEqual(
            submission.documents.first(where: { $0.requestIndex == 1 })?.credentialID,
            "proof-credential"
        )

        await session.emit(.awaitingNextRequest(completedExchanges: 1))
        try await waitUntil { viewModel.sessionState == .awaitingNextRequest(completedExchanges: 1) }
        await session.emit(.reviewRequired(combinedProximityReview(exchange: 2)))
        try await waitUntil { viewModel.review?.exchange == 2 }

        XCTAssertFalse(viewModel.continueAfterResponse)
        XCTAssertNil(viewModel.selections.first(where: { $0.requestIndex == 0 }))
        XCTAssertFalse(viewModel.canApprove)
        let nextReviewID = try XCTUnwrap(viewModel.review?.reviewID)
        XCTAssertNotEqual(nextReviewID, review.reviewID)
        viewModel.decline()
        try await waitUntilAsync { await session.actions.count == 2 }
        let laterActions = await session.actions
        XCTAssertEqual(laterActions.last, .decline(reviewID: nextReviewID))
    }

    @MainActor
    func testPreparingCreatesOneFreshSessionReopensNfcAndRevokesOnDismissal() async throws {
        let review = combinedProximityReview()
        let bridge = FakePreparedSharingBridge()
        let submission = fixtureSubmission(review)
        let sharing = WalletSDK.ProximityPreparedSharing(review: review, submission: submission,
            expiresAt: Date().addingTimeInterval(60), bridge: bridge)
        let plan = WalletSDK.ProximitySharingPlan(review: review, expiresAt: Date().addingTimeInterval(600),
            readerCertificateSHA256: "test-fingerprint", bridge: FakeSharingPlanBridge(sharing: sharing))
        let session = FakeProximitySession(connectedRoute: .init(engagement: .nfc, transport: .nfc))
        let next = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session, nextSession: next)
        let viewModel = ProximityPresentationViewModel(client: client, hostActions: FakeProximityHostActionExecutor())
        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        await session.emit(.preparationRequired(plan))
        try await waitUntil { viewModel.preparingApproval }
        XCTAssertFalse(viewModel.canApprove)
        viewModel.selectCredential(requestIndex: 0, credentialID: "payment-b")
        viewModel.setContinueAfterResponse(true)
        XCTAssertFalse(viewModel.continueAfterResponse)
        viewModel.approve()
        viewModel.approve()
        try await waitUntil { client.startCount == 2 }
        let oldActions = await session.actions
        XCTAssertTrue(oldActions.isEmpty, "Preparation cannot approve the first connection")
        guard case .prepared(let issued) = client.lastConfiguration?.approval else {
            return XCTFail("Expected the explicit approval in the new configuration")
        }
        XCTAssertTrue(issued === sharing)
        await next.emit(.engagementReady([.qr(payload: "mdoc:new"), .nfc]))
        try await waitUntilAsync { await next.presentNfcCalls == 1 }
        await next.emit(.engagementReady([.qr(payload: "mdoc:new"), .nfc]))
        try await Task.sleep(nanoseconds: 20_000_000)
        let presentCount = await next.presentNfcCalls
        XCTAssertEqual(presentCount, 1)
        viewModel.dismiss()
        try await waitUntilAsync { await bridge.revoked }
        XCTAssertNil(viewModel.review)
        XCTAssertNil(viewModel.preparedSharing)
    }

    @MainActor
    func testPreparedRetryReturnsToReviewBeforeCreatingAnotherSession() async throws {
        let review = combinedProximityReview()
        let sharing = WalletSDK.ProximityPreparedSharing(review: review,
            submission: fixtureSubmission(review), expiresAt: Date().addingTimeInterval(60),
            bridge: FakePreparedSharingBridge())
        let plan = WalletSDK.ProximitySharingPlan(review: review, expiresAt: Date().addingTimeInterval(600),
            readerCertificateSHA256: "test-fingerprint", bridge: FakeSharingPlanBridge(sharing: sharing))
        let session = FakeProximitySession()
        let next = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session, nextSession: next)
        let viewModel = ProximityPresentationViewModel(client: client, hostActions: FakeProximityHostActionExecutor())
        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        await session.emit(.preparationRequired(plan))
        try await waitUntil { viewModel.preparingApproval }
        viewModel.selectCredential(requestIndex: 0, credentialID: "payment-a")
        viewModel.approve()
        try await waitUntil { client.startCount == 2 }
        await next.emit(.failed(.init(category: .policy, code: "prepared_sharing_expired",
            message: "Prepared sharing expired", recovery: .startNewSession)))
        try await waitUntil { viewModel.isTerminal }
        viewModel.restart()
        XCTAssertTrue(viewModel.preparingApproval)
        XCTAssertFalse(viewModel.canApprove, "A new approval requires choosing among multiple credentials again")
        XCTAssertEqual(client.startCount, 2)
        XCTAssertNil(viewModel.preparedSharing)
        viewModel.dismiss()
    }

    @MainActor
    func testBackgroundDuringPreparedPermissionSetupRevokesAndCloses() async throws {
        let review = combinedProximityReview()
        let bridge = FakePreparedSharingBridge()
        let sharing = WalletSDK.ProximityPreparedSharing(review: review, submission: fixtureSubmission(review),
            expiresAt: Date().addingTimeInterval(60), bridge: bridge)
        let plan = WalletSDK.ProximitySharingPlan(review: review, expiresAt: Date().addingTimeInterval(600),
            readerCertificateSHA256: "fixture", bridge: FakeSharingPlanBridge(sharing: sharing))
        let first = FakeProximitySession()
        let next = FakeProximitySession()
        let client = FakeProximityWalletClient(session: first, nextSession: next)
        let viewModel = ProximityPresentationViewModel(client: client, hostActions: FakeProximityHostActionExecutor())
        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        await first.emit(.preparationRequired(plan))
        try await waitUntil { viewModel.preparingApproval }
        viewModel.selectCredential(requestIndex: 0, credentialID: "payment-a")
        viewModel.approve()
        try await waitUntil { client.startCount == 2 }
        let checking = WalletSDK.ProximityState.checkingPrerequisites(makeProximityCapabilities(bluetoothAvailable: false, nfcAvailable: false))
        await next.emit(checking)
        try await waitUntil { viewModel.sessionState == checking }
        viewModel.handleLifecycleInterruption()
        try await waitUntilAsync { await bridge.revoked }
        try await waitUntilAsync { await next.closeCount == 1 }
        XCTAssertFalse(viewModel.active)
        XCTAssertNil(viewModel.preparedSharing)
    }

    @MainActor
    func testPreparedSharingScreensRenderReviewReadyAndReceipt() async throws {
        let base = combinedProximityReview()
        let review = WalletSDK.ProximityReview(reviewID: base.reviewID, exchange: 1,
            documents: [WalletSDK.ProximityDocumentReview(requestIndex: 0, documentType: "org.iso.18013.5.1.mDL",
                credentialOptions: [.init(credentialID: "credential", label: "Mobile Driving Licence", issuer: "Example issuer",
                    validUntil: Date(timeIntervalSince1970: 1_893_456_000), deviceAuthentication: .signature,
                    requestedElements: [.init(namespace: "org.iso.18013.5.1", elementIdentifier: "given_name",
                        intentToRetain: false, satisfiesRequestedElements: [])])])],
            readerAuthentication: [.init(scope: .wholeRequest, authenticationIndex: 0,
                outcome: .valid(.init(state: .trusted, certificatePath: .valid, displayName: "City service desk")))],
            readerAuthenticationSummary: .trusted, useCases: [], applicationAuthorizations: [])
        let selection = fixtureSubmission(review)
        let sharing = WalletSDK.ProximityPreparedSharing(review: review, submission: selection,
            expiresAt: Date().addingTimeInterval(60), bridge: FakePreparedSharingBridge())
        let plan = WalletSDK.ProximitySharingPlan(review: review, expiresAt: Date().addingTimeInterval(600),
            readerCertificateSHA256: "fixture-certificate", bridge: FakeSharingPlanBridge(sharing: sharing))
        let first = FakeProximitySession(connectedRoute: .init(engagement: .nfc, transport: .nfc))
        let next = FakeProximitySession()
        let client = FakeProximityWalletClient(session: first, nextSession: next)
        let wallet = WalletViewModel(walletID: "proximity-ui-fixture", walletClient: MockWalletClient(), proximityWalletClient: client)
        wallet.selectedTab = .present
        wallet.dismissStatus()
        let viewModel = wallet.proximityPresentation
        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        await first.emit(.preparationRequired(plan))
        try await waitUntil { viewModel.preparingApproval }
        XCTAssertTrue(viewModel.canApprove)

        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first { $0.isKeyWindow }
        let window = UIWindow(windowScene: scene)
        window.rootViewController = UIHostingController(rootView: PresentView(viewModel: wallet))
        window.makeKeyAndVisible()
        defer { viewModel.dismiss(); window.isHidden = true; previous?.makeKeyAndVisible() }
        func capture(_ name: String) async throws {
            try await Task.sleep(nanoseconds: 500_000_000)
            window.layoutIfNeeded()
            let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
                XCTAssertTrue(window.drawHierarchy(in: window.bounds, afterScreenUpdates: true))
            }
            let attachment = XCTAttachment(image: image)
            attachment.name = name
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        try await capture("native-preparation")
        viewModel.approve()
        try await waitUntil { client.startCount == 2 }
        await next.emit(.engagementReady([.nfc]))
        try await waitUntil { viewModel.displayedEngagement == .nfc }
        try await capture("native-prepared-ready")
        await next.emit(.completed(exchanges: 1, declined: false,
            receipt: .init(review: review, submission: selection, approvalTiming: .beforeConnection, completedAt: Date())))
        try await waitUntil { viewModel.isTerminal }
        try await capture("native-sharing-receipt")
    }

    @MainActor
    func testQRCodeIsScannableInsideWalletChromeAtPhoneSizes() async throws {
        try await assertScannableWalletQr(name: "phone", size: CGSize(width: 390, height: 844))
    }

    @MainActor
    func testQRCodeIsScannableInsideWalletChromeOnSmallPhone() async throws {
        try await assertScannableWalletQr(name: "small", size: CGSize(width: 360, height: 640))
    }

    @MainActor
    private func assertScannableWalletQr(name: String, size: CGSize) async throws {
        let payload = "mdoc:" + String(repeating: "A7v9kQ2_x-", count: 30)
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        let wallet = WalletViewModel(walletID: "proximity-qr-layout-fixture", walletClient: MockWalletClient(), proximityWalletClient: client)
        wallet.selectedTab = .present
        wallet.dismissStatus()
        let model = wallet.proximityPresentation
        model.start()
        try await waitUntil { client.startCount == 1 }
        await session.emit(.engagementReady([.qr(payload: payload), .nfc]))
        try await waitUntil { model.engagementChoices.count == 2 }
        model.showEngagement(.qr)
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let previous = scene.windows.first { $0.isKeyWindow }
        let window = UIWindow(windowScene: scene)
        defer { model.dismiss(); window.isHidden = true; previous?.makeKeyAndVisible() }
            window.rootViewController = UIHostingController(rootView: HomeView(viewModel: wallet)
                .frame(width: size.width, height: size.height)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading))
            window.makeKeyAndVisible()
            try await Task.sleep(nanoseconds: 700_000_000)
            window.layoutIfNeeded()
            let image = UIGraphicsImageRenderer(bounds: window.bounds).image { _ in
                XCTAssertTrue(window.drawHierarchy(in: window.bounds, afterScreenUpdates: true))
            }
            let attachment = XCTAttachment(image: image)
            attachment.name = "native-qr-\(name)"
            attachment.lifetime = .keepAlways
            add(attachment)
            let result = try XCTUnwrap(try ZXIBarcodeReader().read(try XCTUnwrap(image.cgImage)).first)
            XCTAssertEqual(result.text, payload, "Full QR must be scannable without scrolling at \(size)")
    }

    func testQRCodeRendererRoundTripsRealisticLongDeviceEngagementPayload() throws {
        let payload = "mdoc:" + String(repeating: "A7v9kQ2_x-", count: 120)
        let image = try XCTUnwrap(
            WalletQRCodeRenderer.proximityImage(payload: payload)
        )
        let cgImage = try XCTUnwrap(image.cgImage)
        let result = try XCTUnwrap(try ZXIBarcodeReader().read(cgImage).first)

        XCTAssertEqual(result.text, payload)
        XCTAssertEqual(result.bytes as Data, Data(payload.utf8))
    }

    func testQRCodeRendererRejectsUnsupportedProximityPayloadsAndOversizeText() {
        XCTAssertNil(WalletQRCodeRenderer.proximityImage(payload: "https://example.com"))
        XCTAssertNil(WalletQRCodeRenderer.proximityImage(payload: "mdoc:é"))
        XCTAssertNil(
            WalletQRCodeRenderer.proximityImage(
                payload: "mdoc:" + String(repeating: "A", count: 4_000)
            )
        )
    }

    @MainActor
    func testConfigurationProviderIsResolvedOncePerSession() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        var policy = WalletSDK.ProximityReaderPolicy.allowAnonymousOrUntrusted
        var resolutionCount = 0
        let viewModel = ProximityPresentationViewModel(
            client: client,
            configurationProvider: {
                resolutionCount += 1
                return WalletSDK.ProximityConfiguration(readerPolicy: policy)
            },
            hostActions: FakeProximityHostActionExecutor()
        )

        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        XCTAssertEqual(resolutionCount, 1)
        XCTAssertEqual(client.configurations.single?.readerPolicy, .allowAnonymousOrUntrusted)

        policy = .requireTrusted
        viewModel.start()
        XCTAssertEqual(client.startCount, 1)
        XCTAssertEqual(resolutionCount, 1)

        viewModel.dismiss()
        try await waitUntilAsync { await session.closeCount == 1 }
        viewModel.start()
        try await waitUntil { client.startCount == 2 }
        XCTAssertEqual(resolutionCount, 2)
        XCTAssertEqual(client.configurations.last?.readerPolicy, .requireTrusted)
    }

    @MainActor
    func testConfigurationProviderCombinesTransportAndReaderSettings() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        var profile = WalletDemoProximityTransportProfile.provisionalNfcV2Direct
        var policy = WalletSDK.ProximityStoredReaderPolicy.requireTrusted
        let viewModel = ProximityPresentationViewModel(
            client: client,
            configurationProvider: {
                WalletSDK.ProximityReaderTrustSettings(readerPolicy: policy).applying(
                    to: profile.configuration
                )
            },
            hostActions: FakeProximityHostActionExecutor()
        )

        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        let first = try XCTUnwrap(client.lastConfiguration)
        guard case .provisionalNFCV2 = first.session else {
            return XCTFail("The selected transport profile must be preserved")
        }
        XCTAssertEqual(first.readerPolicy, .requireTrusted)

        profile = .defaultProfile
        policy = .allowAnonymousOrUntrusted
        XCTAssertEqual(client.configurations.count, 1)

        viewModel.dismiss()
        try await waitUntilAsync { await session.closeCount == 1 }
        viewModel.start()
        try await waitUntil { client.startCount == 2 }
        let second = try XCTUnwrap(client.lastConfiguration)
        guard case let .nfc(nfc) = second.session, nfc.handover == .negotiatedHandover, nfc.qrFallback != nil else {
            return XCTFail("The next session must use the newly selected transport profile")
        }
        XCTAssertEqual(second.readerPolicy, .allowAnonymousOrUntrusted)
    }

    func testNativeProfilesResolveToTheSameTransportConfigurationsAsCompose() throws {
        let defaultConfiguration = WalletDemoProximityTransportProfile.defaultProfile.configuration
        guard case let .nfc(nfc) = defaultConfiguration.session, nfc.handover == .negotiatedHandover, nfc.qrFallback != nil else {
            return XCTFail("The default profile must use negotiated QR/NFC engagement")
        }
        XCTAssertNotNil(nfc.retrieval.bluetoothLowEnergy)
        XCTAssertNotNil(nfc.retrieval.nfc)

        let hybridConfiguration = WalletDemoProximityTransportProfile
            .provisionalNfcV2Hybrid.configuration
        guard case let .provisionalNFCV2(hybridRetrieval) = hybridConfiguration.session else {
            return XCTFail("The hybrid profile must use NFCv2 engagement and retrieval")
        }
        XCTAssertEqual(hybridRetrieval.bluetoothLowEnergy?.roles, .centralClient)
        XCTAssertEqual(hybridRetrieval.bluetoothLowEnergy?.bearerPolicy, .gattOnly)
        XCTAssertNil(hybridRetrieval.qrFallback)

        let directConfiguration = WalletDemoProximityTransportProfile
            .provisionalNfcV2Direct.configuration
        guard case let .provisionalNFCV2(directRetrieval) = directConfiguration.session else {
            return XCTFail("The direct profile must use NFCv2 engagement and retrieval")
        }
        XCTAssertNil(directRetrieval.bluetoothLowEnergy)
        XCTAssertNil(directRetrieval.qrFallback)
    }

    func testCompatibilityProfilesPreserveEngagementAndNarrowTransfer() {
        for profile in [WalletDemoProximityTransportProfile.bluetooth] {
            guard case .nfc(let session) = profile.configuration.session else { return XCTFail("Expected NFC with QR fallback") }
            XCTAssertEqual(session.handover, .negotiatedHandover)
            XCTAssertEqual(session.retrieval, session.qrFallback)
            XCTAssertNil(session.retrieval.nfc)
            XCTAssertEqual(session.retrieval.bluetoothLowEnergy != nil, profile == .bluetooth)
        }

    }

    func testNativeProfilePersistenceUsesStableComposeValuesAndFallsBackSafely() {
        let suiteName = "id.walt.walletdemo.tests.\(UUID().uuidString)"
        defer { UserDefaults.standard.removePersistentDomain(forName: suiteName) }

        XCTAssertEqual(DemoSharingSettings.proximityApprovalMode(appGroupIdentifier: suiteName), .askEachTime)
        DemoSharingSettings.setProximityApprovalMode(.prepareSharing, appGroupIdentifier: suiteName)
        XCTAssertEqual(DemoSharingSettings.proximityApprovalMode(appGroupIdentifier: suiteName), .prepareSharing)
        XCTAssertEqual(UserDefaults(suiteName: suiteName)?.string(forKey: DemoSharingSettings.proximityApprovalModeKey), "prepare_sharing")
        UserDefaults(suiteName: suiteName)?.set("unknown", forKey: DemoSharingSettings.proximityApprovalModeKey)
        XCTAssertEqual(DemoSharingSettings.proximityApprovalMode(appGroupIdentifier: suiteName), .askEachTime)
        let prepareConfiguration = WalletSDK.ProximityConfiguration().withApproval(.prepareBeforeSharing)
        XCTAssertTrue(prepareConfiguration.toKMPConfiguration().approval is WalletCore.ProximityApprovalPrepareBeforeSharing)

        XCTAssertEqual(
            DemoSharingSettings.proximityTransportProfile(appGroupIdentifier: suiteName),
            .defaultProfile
        )
        DemoSharingSettings.setProximityTransportProfile(
            .provisionalNfcV2Hybrid,
            appGroupIdentifier: suiteName
        )
        XCTAssertEqual(
            DemoSharingSettings.proximityTransportProfile(appGroupIdentifier: suiteName),
            .provisionalNfcV2Hybrid
        )
        XCTAssertEqual(
            UserDefaults(suiteName: suiteName)?
                .string(forKey: DemoSharingSettings.proximityTransportProfileKey),
            "provisional_nfc_v2_hybrid"
        )

        UserDefaults(suiteName: suiteName)?
            .set("unknown_future_profile", forKey: DemoSharingSettings.proximityTransportProfileKey)
        XCTAssertEqual(
            DemoSharingSettings.proximityTransportProfile(appGroupIdentifier: suiteName),
            .defaultProfile
        )
    }

    @MainActor
    func testStartSnapshotsTheSelectedNativeProfileBeforeLaunchingTheSession() async throws {
        var selectedProfile = WalletDemoProximityTransportProfile.defaultProfile
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session, suspendStart: true)
        let viewModel = ProximityPresentationViewModel(
            client: client,
            configurationProvider: { selectedProfile.configuration },
            hostActions: FakeProximityHostActionExecutor()
        )

        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        selectedProfile = .provisionalNfcV2Direct

        let startedConfiguration = try XCTUnwrap(client.lastConfiguration)
        guard case let .nfc(nfc) = startedConfiguration.session, nfc.handover == .negotiatedHandover, nfc.qrFallback != nil else {
            return XCTFail("The active session must retain the profile selected at start")
        }

        viewModel.cancel()
        client.resumeStart()
        try await waitUntilAsync { await session.closeCount == 1 }
    }
    @MainActor
    func testPreparedEngagementSelectionKeepsSessionAndControlsQrVisibility() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        let viewModel = ProximityPresentationViewModel(client: client, hostActions: FakeProximityHostActionExecutor())
        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        await session.emit(.engagementReady([.qr(payload: "mdoc:stable"), .nfc]))
        try await waitUntil { viewModel.engagementChoices.count == 2 }
        XCTAssertNil(viewModel.displayedEngagement)
        XCTAssertNil(viewModel.qrPayload)
        for count in 1...3 {
            viewModel.showEngagement(.qr)
            XCTAssertEqual(viewModel.qrPayload, "mdoc:stable")
            viewModel.showEngagement(.nfc)
            XCTAssertNil(viewModel.qrPayload)
            try await waitUntilAsync { await session.presentNfcCalls == count }
        }
        XCTAssertEqual(client.startCount, 1)
        let closeCount = await session.closeCount
        XCTAssertEqual(closeCount, 0)
        await session.emit(.engagementReady([.qr(payload: "mdoc:stable")]))
        try await waitUntil { viewModel.engagementChoices == [.qr] }
        viewModel.showEngagement(.nfc)
        XCTAssertEqual(viewModel.displayedEngagement, .qr)
        XCTAssertEqual(viewModel.qrPayload, "mdoc:stable")
        viewModel.dismiss()
        XCTAssertNil(viewModel.qrPayload)
        XCTAssertNil(viewModel.preferredEngagement)
    }

    @MainActor
    func testConnectingAndConsentRejectAllConnectionChanges() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        let viewModel = ProximityPresentationViewModel(client: client, hostActions: FakeProximityHostActionExecutor())
        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        for state in [WalletSDK.ProximityState.connecting([.qr(payload: "mdoc:stable")]), .reviewRequired(combinedProximityReview())] {
            await session.emit(state)
            try await waitUntil { viewModel.sessionState == state }
            viewModel.showEngagement(.qr)
            XCTAssertEqual(viewModel.sessionState, state)
            XCTAssertTrue(viewModel.engagementChoices.isEmpty)
            XCTAssertNil(viewModel.qrPayload)
            XCTAssertEqual(client.startCount, 1)
            let closeCount = await session.closeCount
            XCTAssertEqual(closeCount, 0)
        }
        XCTAssertFalse(viewModel.canApprove)
        viewModel.selectCredential(requestIndex: 0, credentialID: "payment-a")
        XCTAssertTrue(viewModel.canApprove)
        viewModel.dismiss()
    }

    @MainActor
    func testOptionalPermissionCanBeSkippedOnlyWithViableAlternative() async throws {
        for nfcAvailable in [false, true] {
            let session = FakeProximitySession()
            let capabilities = makeProximityCapabilities(bluetoothAvailable: false, nfcAvailable: nfcAvailable,
                bluetoothRemediation: [.requestBluetoothPermission])
            let client = FakeProximityWalletClient(session: session, capabilityResults: [capabilities])
            let host = FakeProximityHostActionExecutor()
            let viewModel = ProximityPresentationViewModel(client: client, hostActions: host)
            viewModel.start()
            try await waitUntil { viewModel.capabilities != nil }
            viewModel.continueWithAvailableConnection()
            if nfcAvailable { try await waitUntil { client.startCount == 1 } }
            else { XCTAssertEqual(client.startCount, 0) }
            XCTAssertTrue(host.actions.isEmpty)
            viewModel.dismiss()
        }
    }

    @MainActor
    func testRetryWaitsForCleanupBeforeStartingFreshSession() async throws {
        let session = FakeProximitySession(suspendClose: true)
        let client = FakeProximityWalletClient(session: session)
        let viewModel = ProximityPresentationViewModel(client: client, hostActions: FakeProximityHostActionExecutor())
        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        let initialConfiguration = client.lastConfiguration
        await session.emit(.failed(.init(category: .transport, code: "nfc_failed",
            message: "Connection failed", recovery: .startNewSession)))
        try await waitUntil { viewModel.isTerminal }
        viewModel.restart()
        try await waitUntilAsync { await session.closeCount == 1 }
        XCTAssertEqual(client.startCount, 1)
        XCTAssertNil(viewModel.review)
        await session.resumeClose()
        try await waitUntil { client.startCount == 2 }
        XCTAssertEqual(client.lastConfiguration?.session, initialConfiguration?.session)
        viewModel.dismiss()
    }

    @MainActor
    func testTerminalNfcDenialRetainsGuidanceAndUsesFreshSessionAfterSettings() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        let host = FakeProximityHostActionExecutor(suspendAction: true)
        let viewModel = ProximityPresentationViewModel(client: client, hostActions: host)
        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        await session.emit(.failed(.init(category: .capability, code: "nfc_access_not_accepted",
            message: "NFC access was not accepted", recovery: .startNewSession,
            remediationActions: [.openApplicationSettings])))
        try await waitUntil { viewModel.isTerminal }
        viewModel.remediate(.openApplicationSettings)
        try await waitUntil { host.actions == [.openApplicationSettings] }
        let closeCount = await session.closeCount
        XCTAssertEqual(closeCount, 1)
        XCTAssertEqual(client.startCount, 1)
        host.resumeAction()
        try await waitUntil { client.startCount == 2 }
        let actions = await session.actions
        XCTAssertTrue(actions.isEmpty)
        viewModel.dismiss()
    }

    @MainActor
    func testNoDataIsTerminalAndKeepsFinalExchangeUntilDismissal() async throws {
        let session = FakeProximitySession()
        let client = FakeProximityWalletClient(session: session)
        let viewModel = ProximityPresentationViewModel(client: client, hostActions: FakeProximityHostActionExecutor())
        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        await session.emit(.noData(exchange: 2))
        try await waitUntil { viewModel.isTerminal }
        XCTAssertEqual(viewModel.sessionState, .noData(exchange: 2))
        viewModel.dismiss()
        XCTAssertNil(viewModel.sessionState)
    }

    @MainActor
    func testActualConnectedRouteSurvivesSkippedConnectingStateAndCompletion() async throws {
        let route = WalletSDK.ProximityConnectedRoute(engagement: .nfc, transport: .bluetoothLowEnergy)
        let session = FakeProximitySession(connectedRoute: route)
        let client = FakeProximityWalletClient(session: session)
        let viewModel = ProximityPresentationViewModel(client: client, hostActions: FakeProximityHostActionExecutor())
        viewModel.start()
        try await waitUntil { client.startCount == 1 }
        await session.emit(.awaitingRequest(exchange: 1))
        try await waitUntil { viewModel.connectedRoute == route }
        await session.emit(.completed(exchanges: 1, declined: false))
        try await waitUntil { viewModel.isTerminal }
        XCTAssertEqual(viewModel.connectedRoute, route)
        viewModel.dismiss()
        XCTAssertNil(viewModel.connectedRoute)
    }


}

private func fixtureSubmission(_ review: WalletSDK.ProximityReview) -> WalletSDK.ProximitySubmission {
    .init(documents: review.documents.map { document in
        let credential = document.credentialOptions[0]
        return .init(requestIndex: document.requestIndex, credentialID: credential.credentialID,
            disclosedElements: Set(credential.requestedElements.map {
                .init(namespace: $0.namespace, elementIdentifier: $0.elementIdentifier)
            }))
    })
}

private func combinedProximityReview(exchange: Int = 1) -> WalletSDK.ProximityReview {
    let familyName = WalletSDK.ProximityRequestedElement(
        namespace: "org.iso.18013.5.1",
        elementIdentifier: "family_name",
        intentToRetain: true,
        satisfiesRequestedElements: []
    )
    let eligibility = WalletSDK.ProximityRequestedElement(
        namespace: "org.waltid.example.proof",
        elementIdentifier: "eligible",
        intentToRetain: false,
        satisfiesRequestedElements: []
    )
    func credential(
        id: String,
        label: String,
        elements: [WalletSDK.ProximityRequestedElement]
    ) -> WalletSDK.ProximityCredentialOption {
        WalletSDK.ProximityCredentialOption(
            credentialID: id,
            label: label,
            issuer: "Example issuer",
            validUntil: .distantFuture,
            deviceAuthentication: .signature,
            requestedElements: elements
        )
    }
    return WalletSDK.ProximityReview(
        reviewID: WalletSDK.ProximityReviewID(value: UUID().uuidString),
        exchange: exchange,
        documents: [
            WalletSDK.ProximityDocumentReview(
                requestIndex: 0,
                documentType: "org.waltid.example.payment",
                credentialOptions: [
                    credential(id: "payment-a", label: "Payment credential A", elements: [familyName]),
                    credential(id: "payment-b", label: "Payment credential B", elements: [familyName]),
                ]
            ),
            WalletSDK.ProximityDocumentReview(
                requestIndex: 1,
                documentType: "org.waltid.example.proof",
                credentialOptions: [
                    credential(id: "proof-credential", label: "Proof of eligibility", elements: [eligibility])
                ]
            ),
        ],
        readerAuthentication: [],
        readerAuthenticationSummary: .absent,
        useCases: [],
        applicationAuthorizations: []
    )

}

@MainActor
private final class FakeProximityWalletClient: ProximityWalletClient {
    private let session: any DemoProximityPresentationSession
    private let nextSession: (any DemoProximityPresentationSession)?
    private let suspendStart: Bool
    private let capabilityResults: [WalletSDK.ProximityCapabilities]
    private var startContinuation: CheckedContinuation<Void, Never>?
    private(set) var capabilityCallCount = 0
    private(set) var startCount = 0
    private(set) var configurations: [WalletSDK.ProximityConfiguration] = []
    var lastConfiguration: WalletSDK.ProximityConfiguration? { configurations.last }

    init(
        session: any DemoProximityPresentationSession,
        nextSession: (any DemoProximityPresentationSession)? = nil,
        suspendStart: Bool = false,
        capabilityResults: [WalletSDK.ProximityCapabilities] = [makeProximityCapabilities()]
    ) {
        self.session = session
        self.nextSession = nextSession
        self.suspendStart = suspendStart
        self.capabilityResults = capabilityResults
    }

    func proximityPresentationCapabilities(
        configuration: WalletSDK.ProximityConfiguration
    ) async throws -> WalletSDK.ProximityCapabilities {
        let index = min(capabilityCallCount, capabilityResults.count - 1)
        capabilityCallCount += 1
        return capabilityResults[index]
    }

    func startProximityPresentation(
        configuration: WalletSDK.ProximityConfiguration
    ) async throws -> any DemoProximityPresentationSession {
        startCount += 1
        configurations.append(configuration)
        if suspendStart {
            await withCheckedContinuation { startContinuation = $0 }
        }
        return startCount > 1 ? nextSession ?? session : session
    }

    func resumeStart() {
        let continuation = startContinuation
        startContinuation = nil
        continuation?.resume()
    }
}

private extension Collection {
    var single: Element? { count == 1 ? first : nil }
}

private actor FakeProximitySession: DemoProximityPresentationSession {
    nonisolated let presentment = FakePresentmentState()
    nonisolated let connectedRoute: WalletSDK.ProximityConnectedRoute?
    private var suspendClose: Bool
    private var closeContinuation: CheckedContinuation<Void, Never>?
    nonisolated var systemPresentationActive: Bool { presentment.active }
    nonisolated let states: AsyncStream<WalletSDK.ProximityState>
    private let continuation: AsyncStream<WalletSDK.ProximityState>.Continuation
    private(set) var actions: [WalletSDK.ProximityAction] = []
    private(set) var closeCount = 0
    private(set) var presentNfcCalls = 0

    func presentNfc() async { presentNfcCalls += 1 }

    init(suspendClose: Bool = false, connectedRoute: WalletSDK.ProximityConnectedRoute? = nil) {
        self.suspendClose = suspendClose
        self.connectedRoute = connectedRoute
        var continuation: AsyncStream<WalletSDK.ProximityState>.Continuation!
        states = AsyncStream { continuation = $0 }
        self.continuation = continuation
    }

    func emit(_ state: WalletSDK.ProximityState) {
        continuation.yield(state)
    }

    func dispatch(_ action: WalletSDK.ProximityAction) async throws -> WalletSDK.ProximityActionResult {
        actions.append(action)
        return .accepted
    }

    func close() async {
        closeCount += 1
        if suspendClose { await withCheckedContinuation { closeContinuation = $0 } }
        continuation.finish()
    }

    func resumeClose() {
        suspendClose = false
        let continuation = closeContinuation
        closeContinuation = nil
        continuation?.resume()
    }
}

@MainActor
private final class FakeProximityHostActionExecutor: ProximityHostActionExecutor {
    private(set) var actions: [WalletSDK.ProximityRemediationAction] = []
    private var suspendAction: Bool
    private var continuation: CheckedContinuation<Void, Never>?

    init(suspendAction: Bool = false) { self.suspendAction = suspendAction }

    func resumeAction() {
        suspendAction = false
        let pending = continuation
        continuation = nil
        pending?.resume()
    }

    func perform(
        _ action: WalletSDK.ProximityRemediationAction
    ) async -> WalletSDK.ProximityHostActionResult {
        actions.append(action)
        if suspendAction { await withCheckedContinuation { continuation = $0 } }
        return .completed
    }
}

private func makeProximityCapabilities(
    bluetoothAvailable: Bool = true,
    nfcAvailable: Bool = true,
    bluetoothRemediation: [WalletSDK.ProximityRemediationAction] = []
) -> WalletSDK.ProximityCapabilities {
    func capability(
        available: Bool,
        selected: Bool,
        remediation: [WalletSDK.ProximityRemediationAction] = []
    ) -> WalletSDK.ProximityTransportCapability {
        WalletSDK.ProximityTransportCapability(
            implemented: true,
            profilePermitted: true,
            runtime: !selected ? .notChecked : available ? .available : .unavailable(
                WalletSDK.ProximityError(
                    category: .capability,
                    code: "test_unavailable",
                    message: "The selected test capability is unavailable",
                    recovery: remediation.isEmpty ? .none : .retryPrerequisites
                ),
                remediationActions: remediation
            ),
            selected: selected
        )
    }

    return WalletSDK.ProximityCapabilities(
        profile: .iso180135Edition2DIS2026,
        session: WalletDemoProximityTransportProfile.defaultProfile.configuration.session,
        qrEngagement: capability(available: true, selected: true),
        nfcEngagement: capability(available: true, selected: true),
        bluetoothLowEnergy: capability(
            available: bluetoothAvailable,
            selected: true,
            remediation: bluetoothRemediation
        ),
        nfcRetrieval: capability(available: nfcAvailable, selected: true),
        nfcV2Retrieval: capability(available: false, selected: false),
        wifiAwareRetrieval: capability(available: false, selected: false)
    )
}

@MainActor
private func waitUntil(
    timeout: TimeInterval = 2,
    condition: @escaping @MainActor () -> Bool
) async throws {
    let deadline = Date().addingTimeInterval(timeout)
    while !condition() {
        if Date() >= deadline { XCTFail("Timed out waiting for condition"); return }
        try await Task.sleep(nanoseconds: 10_000_000)
    }
}

private func waitUntilAsync(
    timeout: TimeInterval = 2,
    condition: @escaping () async -> Bool
) async throws {
    let deadline = Date().addingTimeInterval(timeout)
    while !(await condition()) {
        if Date() >= deadline { XCTFail("Timed out waiting for condition"); return }
        try await Task.sleep(nanoseconds: 10_000_000)
    }
}

private final class FakePresentmentState: @unchecked Sendable {
    private let lock = NSLock()
    private var value = false
    var active: Bool {
        lock.lock()
        defer { lock.unlock() }
        return value
    }
    func setActive(_ value: Bool) {
        lock.lock()
        self.value = value
        lock.unlock()
    }
}

private struct FakeSharingPlanBridge: ProximitySharingPlanBridge {
    let sharing: WalletSDK.ProximityPreparedSharing
    var isExpired: Bool { false }
    func approve(_ submission: WalletSDK.ProximitySubmission) throws -> WalletSDK.ProximityPreparationResult {
        .prepared(sharing)
    }
}

private actor FakePreparedSharingBridge: ProximityPreparedSharingBridge {
    nonisolated let remainingSeconds = 60
    private(set) var revoked = false
    func revoke() { revoked = true }
}
