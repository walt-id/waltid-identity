// swift-tools-version: 5.9
import PackageDescription
let package = Package(
  name: "KotlinMultiplatformLinkedPackage",
  platforms: [
    .iOS("15.4")
  ],
  products: [
    .library(
      name: "KotlinMultiplatformLinkedPackage",
      type: .none,
      targets: ["KotlinMultiplatformLinkedPackage"]
    )
  ],
  dependencies: [
    .package(path: "subpackages/_waltid_libraries_protocols_waltid_openid4vc_wallet_persistence_mobile")
  ],
  targets: [
    .target(
      name: "KotlinMultiplatformLinkedPackage",
      dependencies: [
        .product(name: "_waltid_libraries_protocols_waltid_openid4vc_wallet_persistence_mobile", package: "_waltid_libraries_protocols_waltid_openid4vc_wallet_persistence_mobile")
      ]
    )
  ]
)
