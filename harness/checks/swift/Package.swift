// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ScenarioBench",
    platforms: [.macOS(.v13)],
    dependencies: [
        .package(url: "https://github.com/ordo-one/benchmark", .upToNextMajor(from: "1.4.0")),
    ],
    targets: [
        .executableTarget(
            name: "ScenarioBench",
            dependencies: [.product(name: "Benchmark", package: "benchmark")],
            path: "Benchmarks/ScenarioBench",
            plugins: [.plugin(name: "BenchmarkPlugin", package: "benchmark")]
        ),
    ]
)
