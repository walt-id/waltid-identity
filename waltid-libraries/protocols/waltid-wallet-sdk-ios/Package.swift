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
    dependencies: [
        .package(url: "https://github.com/swiftlang/swift-docc-plugin", from: "1.5.0"),
        .package(url: "https://github.com/sqlcipher/SQLCipher.swift.git", exact: "4.16.0"),
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
                .product(
                    name: "SQLCipher",
                    package: "SQLCipher.swift",
                    condition: .when(platforms: [.iOS])
                ),
            ]
        ),
        .testTarget(
            name: "WalletSDKTests",
            dependencies: ["WalletSDK"]
        ),
    ]
)
