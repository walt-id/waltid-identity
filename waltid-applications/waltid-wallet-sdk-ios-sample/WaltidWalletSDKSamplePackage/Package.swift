// swift-tools-version: 6.1
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "WaltidWalletSDKSampleFeature",
    platforms: [.iOS(.v15)],
    products: [
        // Products define the executables and libraries a package produces, making them visible to other packages.
        .library(
            name: "WaltidWalletSDKSampleFeature",
            targets: ["WaltidWalletSDKSampleFeature"]
        ),
    ],
    dependencies: [
        .package(path: "../../../waltid-libraries/protocols/waltid-wallet-sdk-ios"),
    ],
    targets: [
        // Targets are the basic building blocks of a package, defining a module or a test suite.
        // Targets can depend on other targets in this package and products from dependencies.
        .target(
            name: "WaltidWalletSDKSampleFeature",
            dependencies: [
                .product(name: "WaltidWalletSDK", package: "waltid-wallet-sdk-ios"),
            ]
        ),
        .testTarget(
            name: "WaltidWalletSDKSampleFeatureTests",
            dependencies: [
                "WaltidWalletSDKSampleFeature",
                .product(name: "WaltidWalletSDK", package: "waltid-wallet-sdk-ios"),
            ]
        ),
    ]
)
