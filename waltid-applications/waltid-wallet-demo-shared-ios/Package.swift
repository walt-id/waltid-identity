// swift-tools-version: 5.9

import PackageDescription

// Provider orchestration and sharing-review UI shared by the native and Compose demos. It is
// deliberately not a public walt.id framework: it exists so the two demos cannot drift apart in the
// parts that decide whether the platform sees them as one wallet, nor in what the user is shown
// before credential data leaves the device.
let package = Package(
    name: "WalletDemoIdentityDocumentSupport",
    platforms: [
        .iOS("15.4"),
    ],
    products: [
        .library(
            name: "WalletDemoIdentityDocumentSupport",
            targets: ["WalletDemoIdentityDocumentSupport"]
        ),
        // Split out so a demo app can render the sharing review without linking the provider
        // orchestration, and so the UI target cannot reach for IdentityDocumentServices: the review
        // renders a value model, and every Apple request object stays on the provider side.
        .library(
            name: "WalletDemoSharingUI",
            targets: ["WalletDemoSharingUI"]
        ),
    ],
    dependencies: [
        .package(path: "../../waltid-libraries/protocols/waltid-wallet-sdk-ios"),
        .package(url: "https://github.com/zxing-cpp/zxing-cpp.git", exact: "3.1.1"),
    ],
    targets: [
        .target(
            name: "WalletDemoSharingUI",
            dependencies: [
                "WalletDemoQRCodeCore",
                .product(name: "WalletSDK", package: "waltid-wallet-sdk-ios"),
            ],
            resources: [
                .process("Resources"),
            ]
        ),
        .target(
            name: "WalletDemoIdentityDocumentSupport",
            dependencies: [
                "WalletDemoSharingUI",
                .product(name: "WalletSDK", package: "waltid-wallet-sdk-ios"),
            ]
        ),
        // Private adapter for the one ZXing-C++ writer mode not exposed by its Swift wrapper:
        // ASCII QR text without forcing an ECI marker.
        .target(
            name: "WalletDemoQRCodeCore",
            dependencies: [
                .product(name: "ZXingCpp", package: "zxing-cpp"),
            ],
            publicHeadersPath: "include",
            linkerSettings: [
                .linkedFramework("CoreGraphics"),
            ]
        ),
    ]
)

// No test target here on purpose: WalletSDK links WalletCore.xcframework, which has no macOS slice,
// so `swift test` cannot build this package. The tests for its pure logic live in the demo apps'
// simulator test targets, which is also the only iOS test command this repo's CI runs.
