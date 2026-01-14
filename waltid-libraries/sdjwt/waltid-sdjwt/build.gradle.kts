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
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("com.soywiz.korlibs.krypto:krypto:4.0.10")
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
            implementation("org.slf4j:slf4j-simple:2.0.17")
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
