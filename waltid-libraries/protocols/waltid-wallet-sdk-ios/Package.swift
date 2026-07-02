// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "WaltidWalletSDK",
    platforms: [
        .iOS(.v15),
        .macOS(.v10_15),
    ],
    products: [
        .library(
            name: "WaltidWalletSDK",
            targets: ["WaltidWalletSDK"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "WaltidWalletCore",
            path: "../waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WaltidWalletCore.xcframework"
        ),
        .target(
            name: "WaltidWalletSDK",
            dependencies: [
                .target(
                    name: "WaltidWalletCore",
                    condition: .when(platforms: [.iOS])
                ),
            ],
            linkerSettings: [
                .linkedLibrary("sqlite3", .when(platforms: [.iOS])),
            ]
        ),
        .testTarget(
            name: "WaltidWalletSDKTests",
            dependencies: ["WaltidWalletSDK"]
        ),
    ]
)
