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
  ],
  targets: [
    .target(
      name: "KotlinMultiplatformLinkedPackage",
      linkerSettings: [
        .linkedLibrary("sqlite3")
      ]
    )
  ]
)
