// swift-tools-version: 5.9

import PackageDescription

// Private demo/test support. The profile is also a build input for the Kotlin adapters.
let package = Package(
    name: "WalletDemoKeyAttestation",
    platforms: [.iOS("15.4")],
    products: [
        .library(name: "WalletDemoKeyAttestation", targets: ["WalletDemoKeyAttestation"]),
    ],
    dependencies: [
        .package(path: "../../../waltid-libraries/protocols/waltid-wallet-sdk-ios"),
    ],
    targets: [
        .target(
            name: "WalletDemoKeyAttestation",
            dependencies: [.product(name: "WalletSDK", package: "waltid-wallet-sdk-ios")],
            path: ".",
            exclude: ["kotlin", "sources.gradle.kts", "README.md"],
            sources: ["swift"],
            resources: [.process("Resources")]
        ),
    ]
)
