@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("waltid.jvm.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto2"

kotlin {
    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }
}

dependencies {
    api(project(":waltid-libraries:crypto:waltid-crypto2"))
    implementation(identityLibs.kotlinx.serialization.json)
    implementation(identityLibs.kotlinx.coroutines.core)
    implementation(identityLibs.bouncycastle.prov)
    implementation(identityLibs.bouncycastle.pkix)

    testImplementation(kotlin("test"))
    testImplementation(identityLibs.kotlinx.coroutines.test)
    testImplementation(identityLibs.junit.jupiter.api)
    testImplementation(project(":waltid-libraries:crypto:waltid-jose"))
    testImplementation(project(":waltid-libraries:crypto:waltid-cose"))
}

val softHsmExecutable = listOf("/usr/bin/softhsm2-util", "/usr/sbin/softhsm2-util")
    .map(::file)
    .firstOrNull { it.isFile }
val softHsmLibrary = listOf("/usr/lib/softhsm/libsofthsm2.so", "/usr/lib/pkcs11/libsofthsm2.so")
    .map(::file)
    .firstOrNull { it.isFile }
val softHsmConfigPath = layout.buildDirectory.file("softhsm-test/softhsm2.conf").get().asFile.absolutePath

tasks.withType<Test> {
    useJUnitPlatform()
    if (softHsmExecutable != null && softHsmLibrary != null) {
        environment("SOFTHSM2_CONF", softHsmConfigPath)
        systemProperty("waltid.test.softhsm.library", softHsmLibrary.absolutePath)
        systemProperty("waltid.test.softhsm.executable", softHsmExecutable.absolutePath)
        systemProperty("waltid.test.softhsm.config", softHsmConfigPath)
    }
    // Forwarded so VendorTokenSmokeTest can be pointed at a real token (Luna, tpm2-pkcs11, opencryptoki, ...) from
    // the caller's environment. Gradle test JVMs do not inherit the daemon's environment, so without this the test
    // would silently skip. TPM2_PKCS11_STORE and OPENCRYPTOKI_* are vendor library settings, not our own.
    listOf(
        "WALTID_PKCS11_LIBRARY",
        "WALTID_PKCS11_PIN",
        "WALTID_PKCS11_SLOT_ID",
        "WALTID_PKCS11_SLOT_LIST_INDEX",
        "TPM2_PKCS11_STORE",
        "TPM2_PKCS11_TCTI",
        "OPENCRYPTOKI_CONF",
    ).forEach { name -> System.getenv(name)?.let { environment(name, it) } }
}

mavenPublishing {
    pom {
        name.set("walt.id crypto2 PKCS11 provider")
        description.set("JVM PKCS11 and HSM provider for walt.id crypto2")
    }
}
