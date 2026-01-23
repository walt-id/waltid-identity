plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.protocols"

dependencies {
    // Walt.id
    api(project(":waltid-services:waltid-service-commons"))
    implementation(project(":waltid-libraries:protocols:waltid-openid4vp-verifier"))
    implementation(project(":waltid-libraries:credentials:waltid-dcql"))
    implementation(project(":waltid-libraries:credentials:waltid-verification-policies2"))
    implementation(project(":waltid-libraries:credentials:waltid-verification-policies2-vp"))

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

mavenPublishing {
    pom {
        name.set("walt.id Verifier SDK - OpenID4VP version - OpenAPI documentation blocks")
        description.set("walt.id Kotlin/Java Verifier for OpenID4VP - OpenAPI documentation blocks")
    }
}
