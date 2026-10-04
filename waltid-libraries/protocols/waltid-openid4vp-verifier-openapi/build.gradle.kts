plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.protocols"

dependencies {
    // walt.id
    implementation(project(":waltid-libraries:protocols:waltid-openid4vp-verifier"))
    implementation(project(":waltid-libraries:credentials:waltid-dcql"))
    implementation(project(":waltid-libraries:credentials:waltid-verification-policies2"))
    implementation(project(":waltid-libraries:credentials:waltid-verification-policies2-vp"))

    // OpenAPI route documentation
    api(identityLibs.smiley.ktor.openapi)
    api(identityLibs.ktor.http)

    // JSON
    implementation(identityLibs.kotlinx.serialization.json)
}

mavenPublishing {
    pom {
        name.set("walt.id Verifier SDK - OpenID4VP version - OpenAPI documentation blocks")
        description.set("walt.id Kotlin/Java Verifier for OpenID4VP - OpenAPI documentation blocks")
    }
}
