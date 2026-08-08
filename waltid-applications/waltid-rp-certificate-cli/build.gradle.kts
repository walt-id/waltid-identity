plugins {
    id("waltid.jvm.library")
    application
}

group = "id.walt.rpcert.cli"

dependencies {
    // Walt.id libraries
    implementation(project(":waltid-libraries:protocols:waltid-relying-party-certificate"))
    implementation(project(":waltid-libraries:protocols:waltid-openid4vp-wallet"))
    implementation(project(":waltid-libraries:web:waltid-web-data-fetching"))
    implementation(project(":waltid-libraries:protocols:waltid-openid4vp"))
    implementation(project(":waltid-libraries:credentials:waltid-dcql"))
    implementation(project(":waltid-libraries:crypto:waltid-x509"))
    implementation(project(":waltid-libraries:crypto:waltid-crypto"))

    // Kotlinx
    implementation(identityLibs.kotlinx.serialization.json)
    implementation(identityLibs.kotlinx.coroutines.core)

    // Ktor client engine, for fetching request_uri Authorization Requests
    implementation(identityLibs.ktor.client.cio)

    // CLI
    implementation(identityLibs.clikt.core)

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.simple)

    // JOSE/JWT + BouncyCastle, for --generate-demo-ca
    implementation(identityLibs.nimbus.jose.jwt)
    implementation(identityLibs.bouncycastle.prov)
    implementation(identityLibs.bouncycastle.pkix)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
}

application {
    mainClass.set("id.walt.rpcert.cli.MainKt")
}
