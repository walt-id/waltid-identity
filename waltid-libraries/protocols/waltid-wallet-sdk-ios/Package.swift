// swift-tools-version: 5.9

import PackageDescription
import Foundation

let physicalFixtures = ProcessInfo.processInfo.environment["WALLET_SDK_PHYSICAL_FIXTURES"] == "1"
let scaAppFixtures = ProcessInfo.processInfo.environment["WALLET_SCA_APP_E2E"] == "1"
let bridgeFixtures = ProcessInfo.processInfo.environment["WALLET_SDK_BRIDGE_FIXTURES"] == "1"
precondition([physicalFixtures, bridgeFixtures, scaAppFixtures].filter { $0 }.count <= 1, "Select only one isolated test fixture framework")

let package = Package(
    name: "WalletSDK",
    platforms: [.iOS("15.4")],
    products: [
        .library(name: "WalletSDK", targets: ["WalletSDK"]),
        .library(name: "WalletSDKKeychainRecovery", targets: ["WalletSDKKeychainRecovery"]),
        .library(name: "WalletSDKEnterpriseCustody", targets: ["WalletSDKEnterpriseCustody"]),
    ],
    dependencies: [
        .package(url: "https://github.com/swiftlang/swift-docc-plugin", from: "1.5.0"),
        .package(url: "https://github.com/sqlcipher/SQLCipher.swift.git", exact: "4.16.0"),
    ],
    targets: [
        .binaryTarget(
            name: "WalletCore",
            path: scaAppFixtures
                ? "../waltid-openid4vc-wallet-mobile/build/sca-app-e2e/XCFrameworks/release/WalletCore.xcframework"
                : physicalFixtures
                ? "../waltid-openid4vc-wallet-mobile/build/physical-fixtures/XCFrameworks/release/WalletCore.xcframework"
                : bridgeFixtures
                ? "../waltid-openid4vc-wallet-mobile/build/bridge-fixtures/XCFrameworks/release/WalletCore.xcframework"
                : "../waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WalletCore.xcframework"
        ),
        .target(
            name: "WalletSDK",
            dependencies: [
                .target(name: "WalletCore", condition: .when(platforms: [.iOS])),
                .product(name: "SQLCipher", package: "SQLCipher.swift", condition: .when(platforms: [.iOS])),
            ]
        ),
        .target(name: "WalletSDKKeychainRecovery", dependencies: ["WalletSDK"]),
        .target(name: "WalletSDKEnterpriseCustody", dependencies: ["WalletSDK"]),
        .testTarget(
            name: "WalletSDKTests",
            dependencies: ["WalletSDK", "WalletSDKKeychainRecovery", "WalletSDKEnterpriseCustody"],
            swiftSettings: bridgeFixtures ? [.define("WALLET_SDK_BRIDGE_FIXTURES")] : []
        ),
    ]
)
