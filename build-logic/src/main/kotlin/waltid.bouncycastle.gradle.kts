// Use the catalog's mainline jdk18on family. LTS jars contain the same packages
// and must be excluded from direct and transitive dependencies to avoid duplicate classes.
configurations.configureEach {
    exclude(group = "org.bouncycastle", module = "bcprov-lts8on")
    exclude(group = "org.bouncycastle", module = "bcpkix-lts8on")
    exclude(group = "org.bouncycastle", module = "bcutil-lts8on")
}
