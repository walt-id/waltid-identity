plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.credentials"

val generateJsonSchemaTestFixtures = tasks.register("generateJsonSchemaTestFixtures") {
    inputs.files(
        "src/commonTest/resources/json-schema-remote-policy.json",
        "src/commonTest/resources/ob_v3p0_achievementcredential_schema.json",
    )
    outputs.dir(layout.buildDirectory.dir("generated/json-schema-test-fixtures/kotlin"))
    doLast {
        val source = buildString {
            appendLine("// Generated from commonTest/resources. Do not edit.")
            appendLine("package id.walt.policies2.vc")
            appendLine("internal object JsonSchemaTestFixtures {")
            for (file in inputs.files.files.sortedBy { it.name }) {
                val name = when (file.name) {
                    "json-schema-remote-policy.json" -> "issuer"
                    "ob_v3p0_achievementcredential_schema.json" -> "openBadge"
                    else -> error("Unexpected schema fixture: ${file.name}")
                }
                appendLine("    val $name: String = listOf(")
                for (chunk in file.readText().chunked(16_000)) {
                    val literal = groovy.json.JsonOutput.toJson(chunk).replace("$", "\\$")
                    appendLine("        $literal,")
                }
                appendLine("    ).joinToString(\"\")")
            }
            appendLine("}")
        }
        val destination = outputs.files.singleFile.resolve("id/walt/policies2/vc/JsonSchemaTestFixtures.kt")
        destination.parentFile.mkdirs()
        destination.writeText(source)
    }
}

kotlin {

    sourceSets {
        commonTest.get().kotlin.srcDir("../../sdjwt/waltid-sdjwt/src/typeMetadataTestFixtures/kotlin")
        commonTest {
            kotlin.srcDir(generateJsonSchemaTestFixtures.map { it.outputs.files })
        }
        commonMain.dependencies {
            // Coroutines
            implementation(identityLibs.kotlinx.coroutines.core)

            // HTTP
            implementation(identityLibs.bundles.waltid.ktor.client)

            // Logging
            implementation(identityLibs.oshai.kotlinlogging)

            // Kotlinx
            implementation(identityLibs.kotlinx.datetime)
            implementation(identityLibs.kotlinx.io.core)
            implementation(identityLibs.kotlinx.io.bytestring)

            // JSON
            implementation(identityLibs.kotlinx.serialization.json)

            implementation(identityLibs.optimumcode.jsonschemavalidator)
            implementation(identityLibs.jsonpathkt)

            /*
             * walt.id:
             */
            implementation(project(":waltid-libraries:crypto:waltid-x509"))
            implementation(project(":waltid-libraries:crypto:waltid-cose"))
            implementation(project(":waltid-libraries:crypto:waltid-jose"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto2"))
            implementation(project(":waltid-libraries:credentials:waltid-credential-key-resolver"))
            implementation(project(":waltid-libraries:waltid-did"))
            implementation(project(":waltid-libraries:credentials:waltid-vical"))
            implementation(project(":waltid-libraries:credentials:waltid-dcql"))
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials"))
            implementation(project(":waltid-libraries:credentials:waltid-status-list"))
            implementation(project(":waltid-libraries:crypto:waltid-crypto")) // for Base64Utils
            implementation(project(":waltid-libraries:web:waltid-web-data-fetching"))

            implementation(identityLibs.korlibs.io)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
            implementation(identityLibs.ktor.client.mock)
            implementation(project(":waltid-libraries:credentials:waltid-digital-credentials-examples"))
        }
        jvmMain.dependencies {
            // Trust Registry library for inline trust list resolution
            implementation(project(":waltid-libraries:credentials:waltid-trust-registry"))
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
            implementation(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.junit.jupiter.params)
            implementation(identityLibs.ktor.server.test.host)
            implementation(identityLibs.ktor.server.content.negotiation)
            implementation(identityLibs.ktor.server.netty)
            implementation(identityLibs.mockk)
        }
    }
}

/* -- Publishing -- */
mavenPublishing {
    pom {
        name.set("walt.id Verification Policies")
        description.set("walt.id Verification Policies for Kotlin/Java")
    }
}
