// swift-tools-version: 5.9

import PackageDescription

// Provider orchestration shared by the native and Compose demo IdentityDocument extensions. It is
// deliberately not a public walt.id framework: it exists so the two demos cannot drift apart in the
// parts that decide whether the platform sees them as one wallet.
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
    ],
    dependencies: [
        .package(path: "../../waltid-libraries/protocols/waltid-wallet-sdk-ios"),
    ],
    targets: [
        .target(
            name: "WalletDemoIdentityDocumentSupport",
            dependencies: [
                .product(name: "WalletSDK", package: "waltid-wallet-sdk-ios"),
            ]
        ),
    ]
)

// No test target here on purpose: WalletSDK links WalletCore.xcframework, which has no macOS slice,
// so `swift test` cannot build this package. The tests for its pure logic live in the demo apps'
// simulator test targets, which is also the only iOS test command this repo's CI runs.
