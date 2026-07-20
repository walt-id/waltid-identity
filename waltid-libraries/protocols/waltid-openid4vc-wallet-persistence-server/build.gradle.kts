plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.protocols"

dependencies {
    // The base wallet library — store interfaces and data models
    api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))

    // Exposed — SQL framework (api so Database is visible to consumers)
    api(identityLibs.exposed.core)
    api(identityLibs.exposed.jdbc)
    implementation(identityLibs.exposed.java.time)
    implementation(identityLibs.exposed.json)

    // JDBC drivers — SQLite (default) and Postgres (optional)
    implementation(identityLibs.sqlite.jdbc)
    implementation(identityLibs.postgresql)

    // Connection pooling
    implementation(identityLibs.hikaricp)

    // Serialization
    implementation(identityLibs.kotlinx.serialization.json)

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)

    // Coroutines
    implementation(identityLibs.kotlinx.coroutines.core)

    // Tests
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
    testImplementation(identityLibs.sqlite.jdbc)
}

mavenPublishing {
    pom {
        name.set("walt.id Wallet SDK - Persistence (Exposed/SQL)")
        description.set(
            "Exposed/SQL-backed implementations of WalletStore, WalletCredentialStore, " +
                "WalletKeyStore, and WalletDidStore for the walt.id Wallet2. " +
                "Supports SQLite (default) and PostgreSQL."
        )
    }
}
