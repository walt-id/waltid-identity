// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "WaltIDWalletSDK",
    platforms: [
        .iOS("15.4"),
    ],
    products: [
        .library(
            name: "WaltIDWalletSDK",
            targets: ["WaltIDWalletSDK"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "WaltIDWalletCore",
            path: "../waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WaltIDWalletCore.xcframework"
        ),
        .target(
            name: "WaltIDWalletSDK",
            dependencies: [
                .target(
                    name: "WaltIDWalletCore",
                    condition: .when(platforms: [.iOS])
                ),
            ],
            linkerSettings: [
                .linkedLibrary("sqlite3", .when(platforms: [.iOS])),
            ]
        ),
        .testTarget(
            name: "WaltIDWalletSDKTests",
            dependencies: ["WaltIDWalletSDK"]
        ),
    ]
)
