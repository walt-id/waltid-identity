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
            implementation(identityLibs.whyoleg.cryptography.random)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.datetime)
            implementation(identityLibs.kotlincrypto.hash.sha2)
            implementation(identityLibs.kotlincrypto.random)
            implementation(identityLibs.korlibs.encoding)
            implementation(identityLibs.oshai.kotlinlogging)
            api(project(":waltid-libraries:crypto:waltid-crypto"))
            api(project(":waltid-libraries:crypto:waltid-crypto2"))
            api(project(":waltid-libraries:crypto:waltid-jose"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(identityLibs.nimbus.jose.jwt)
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
        }
        jsMain.dependencies {
            implementation(npm("jose", "5.10.0"))
        }
    }

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
