plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.credentials"

dependencies {
    // Serialization
    implementation(identityLibs.kotlinx.serialization.json)
    implementation(identityLibs.kotlinx.datetime)
    implementation(identityLibs.kotlinx.coroutines.core)

    // HTTP fetching
    implementation(identityLibs.ktor.client.core)
    implementation(identityLibs.ktor.client.okhttp)
    implementation(identityLibs.ktor.client.content.negotiation)
    implementation(identityLibs.ktor.serialization.kotlinx.json)

    // Logging
    implementation(identityLibs.oshai.kotlinlogging)

    // Test
    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
    testImplementation("org.slf4j:slf4j-simple:2.0.17")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
