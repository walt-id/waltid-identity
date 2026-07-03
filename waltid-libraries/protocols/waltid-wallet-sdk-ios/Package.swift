// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "WalletSDK",
    platforms: [
        .iOS("15.4"),
    ],
    products: [
        .library(
            name: "WalletSDK",
            targets: ["WalletSDK"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "WalletCore",
            path: "../waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WalletCore.xcframework"
        ),
        .target(
            name: "WalletSDK",
            dependencies: [
                .target(
                    name: "WalletCore",
                    condition: .when(platforms: [.iOS])
                ),
            ],
            linkerSettings: [
                .linkedLibrary("sqlite3", .when(platforms: [.iOS])),
            ]
        ),
        .testTarget(
            name: "WalletSDKTests",
            dependencies: ["WalletSDK"]
        ),
    ]
)
