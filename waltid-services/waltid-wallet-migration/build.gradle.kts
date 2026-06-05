plugins {
    id("waltid.jvm.library")
    application
}

group = "id.walt"

application {
    mainClass.set("id.walt.wallet.migration.WalletMigrationKt")
}

dependencies {
    // New wallet persistence — provides wallet2 table definitions and store impls
    implementation(project(":waltid-libraries:protocols:waltid-openid4vc-wallet-persistence"))

    // Credential parsing — needed to re-parse old raw credential strings
    implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))

    // Serialization
    implementation(identityLibs.kotlinx.serialization.json)

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)
    implementation(identityLibs.slf4j.julbridge)

    // JDBC drivers — source and target databases
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    runtimeOnly("org.postgresql:postgresql:42.7.3")
}
