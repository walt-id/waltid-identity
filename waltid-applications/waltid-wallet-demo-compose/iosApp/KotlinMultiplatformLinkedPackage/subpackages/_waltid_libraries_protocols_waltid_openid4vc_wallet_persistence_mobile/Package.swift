// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "_waltid_libraries_protocols_waltid_openid4vc_wallet_persistence_mobile",
  platforms: [
    .iOS("15.4")
  ],
  products: [
    .library(
      name: "_waltid_libraries_protocols_waltid_openid4vc_wallet_persistence_mobile",
      type: .none,
      targets: ["_waltid_libraries_protocols_waltid_openid4vc_wallet_persistence_mobile"]
    )
  ],
  dependencies: [
    .package(
      url: "https://github.com/sqlcipher/SQLCipher.swift.git",
      exact: "4.16.0"
    )
  ],
  targets: [
    .target(
      name: "_waltid_libraries_protocols_waltid_openid4vc_wallet_persistence_mobile",
      dependencies: [
        .product(
          name: "SQLCipher",
          package: "SQLCipher.swift",
          condition: .when(platforms: [.iOS])
        )
      ]
    )
  ]
)
