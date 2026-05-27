plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.protocols"

dependencies {
    // The base wallet library — store interfaces and data models
    api(project(":waltid-libraries:protocols:waltid-openid4vc-wallet"))

    // Exposed — SQL framework
    implementation("org.jetbrains.exposed:exposed-core:1.3.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.3.0")
    implementation("org.jetbrains.exposed:exposed-java-time:1.3.0")
    implementation("org.jetbrains.exposed:exposed-json:1.3.0")

    // JDBC drivers — SQLite (default) and Postgres (optional)
    implementation("org.xerial:sqlite-jdbc:3.47.0.0")
    compileOnly("org.postgresql:postgresql:42.7.3")

    // Connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Serialization
    implementation(identityLibs.kotlinx.serialization.json)

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)

    // Coroutines
    implementation(identityLibs.kotlinx.coroutines.core)

    // Tests
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
    testImplementation("org.xerial:sqlite-jdbc:3.47.0.0")
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
