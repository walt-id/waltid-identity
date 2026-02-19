plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.sdjwt"

kotlin {
    js(IR) {
        compilerOptions {
            target.set("es2015")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("dev.whyoleg.cryptography:cryptography-random:0.5.0")
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.datetime)
            implementation("com.soywiz:korlibs-crypto:6.0.0")
            implementation("com.soywiz:korlibs-encoding:6.0.0")
            implementation(identityLibs.oshai.kotlinlogging)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation("com.nimbusds:nimbus-jose-jwt:10.6")
            api(project(":waltid-libraries:crypto:waltid-crypto"))
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
        }
        jsMain.dependencies {
            implementation(npm("jose", "5.10.0"))
        }
    }

    applyDefaultHierarchyTemplate()
}

tasks.named("jsBrowserTest") {
    enabled = false
}


mavenPublishing {
    pom {
        name.set("walt.id SD-JWT library")
        description.set("walt.id Kotlin/Java library for SD-JWTs")
    }
}
