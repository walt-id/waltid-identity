plugins {
    id("waltid.base")

    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(project.javaServiceVersion)
}

tasks.withType<Zip> {
    isZip64 = true
}

dependencies {
    implementation(kotlin("stdlib"))
}

// Ktor 3.5.0 pulls Netty 4.2.13. Force the patched 4.2 line onto every service classpath.
val netty42 = project.identityCatalog.findVersion("netty-4_2").get().requiredVersion
configurations.configureEach {
    resolutionStrategy.force(
        "io.netty:netty-codec:$netty42",
        "io.netty:netty-codec-http:$netty42",
        "io.netty:netty-codec-http2:$netty42",
        "io.netty:netty-codec-compression:$netty42",
        "io.netty:netty-handler:$netty42",
        "io.netty:netty-transport-classes-epoll:$netty42",
    )
}
