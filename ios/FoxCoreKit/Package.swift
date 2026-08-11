// swift-tools-version:5.9
import PackageDescription

// Portage Swift de la logique pure du "cerveau" FoxOFF (package Kotlin
// `com.projectfox.foxoff.brain`), indépendante de toute API Apple
// (HealthKit, WatchConnectivity...). Voir ios/FoxCoreKit/README.md.
//
// NON COMPILÉ — écrit sans accès à un Mac/Xcode/toolchain Swift. À valider
// avec `swift build` et `swift test` dès qu'un environnement macOS est
// disponible (voir le plan iOS dans .claude/plans, Étape 2).
let package = Package(
    name: "FoxCoreKit",
    platforms: [
        .iOS(.v16),
        .watchOS(.v9)
    ],
    products: [
        .library(name: "FoxCoreKit", targets: ["FoxCoreKit"])
    ],
    targets: [
        .target(name: "FoxCoreKit"),
        .testTarget(name: "FoxCoreKitTests", dependencies: ["FoxCoreKit"])
    ]
)
