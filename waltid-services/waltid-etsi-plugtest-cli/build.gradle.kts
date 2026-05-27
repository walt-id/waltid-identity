plugins {
    id("waltid.jvm.library")
    application
}

group = "id.walt.etsi"

object Versions {
    const val HOPLITE_VERSION = "2.9.0"
}

dependencies {
    // Walt.id libraries
    implementation(project(":waltid-libraries:crypto:waltid-crypto"))
    implementation(project(":waltid-libraries:crypto:waltid-cose"))
    implementation(project(":waltid-libraries:crypto:waltid-x509"))
    implementation(project(":waltid-libraries:waltid-did"))
    implementation(project(":waltid-libraries:credentials:waltid-w3c-credentials"))
    implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))
    implementation(project(":waltid-libraries:credentials:waltid-mdoc-credentials2"))
    implementation(project(":waltid-libraries:credentials:waltid-verification-policies2"))
    implementation(project(":waltid-libraries:sdjwt:waltid-sdjwt"))

    // Kotlinx
    implementation(identityLibs.kotlinx.serialization.json)
    implementation(identityLibs.kotlinx.datetime)
    implementation(identityLibs.kotlinx.coroutines.core)

    // CLI
    implementation(identityLibs.clikt.core)

    // Config (HOCON)
    implementation("com.sksamuel.hoplite:hoplite-core:${Versions.HOPLITE_VERSION}")
    implementation("com.sksamuel.hoplite:hoplite-hocon:${Versions.HOPLITE_VERSION}")

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
    implementation("org.slf4j:slf4j-simple:2.0.17")

    // JOSE/JWT
    implementation(identityLibs.nimbus.jose.jwt)

    // BouncyCastle for certificate handling
    implementation(identityLibs.bouncycastle.prov)
    implementation(identityLibs.bouncycastle.pkix)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
}

application {
    mainClass.set("id.walt.etsi.MainKt")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "id.walt.etsi.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
