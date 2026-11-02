import org.gradle.api.tasks.testing.Test

// Use the catalog's mainline jdk18on family. LTS jars contain the same packages
// and must be excluded from direct and transitive dependencies to avoid duplicate classes.
configurations.configureEach {
    exclude(group = "org.bouncycastle", module = "bcprov-lts8on")
    exclude(group = "org.bouncycastle", module = "bcpkix-lts8on")
    exclude(group = "org.bouncycastle", module = "bcutil-lts8on")
}

tasks.withType<Test>().configureEach {
    if (project.name != "waltid-web-data-fetching") {
        systemProperty("walt.ssrf.allowLoopback", "true")
    }
}
