// Catalog-backed constraints for patched transitive libraries.
//
// `resolutionStrategy.force` only affects the project that declares it. An `api`
// constraint is part of the published graph, so consumers such as Wallet API2 and
// the enterprise API inherit the same pins.
pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    val catalog = project.identityCatalog
    val constrained = listOf(
        "jackson-core",
        "jackson-databind",
        "jackson-core-tools",
        "jackson-databind-tools",
        "netty-codec-compression",
        "netty-codec-http-v2",
        "netty-codec-http2-v2",
        "netty-handler-v2",
        "netty-transport-classes-epoll",
        "netty-transport-native-epoll",
        "netty-transport-native-kqueue",
        "vertx-core",
        "rhino",
    )

    dependencies {
        constraints {
            constrained.forEach { alias ->
                add("api", catalog.findLibrary(alias).get())
            }
        }
    }
}
