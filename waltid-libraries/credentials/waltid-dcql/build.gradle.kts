import java.security.MessageDigest

plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.dcql"

val generateBundledJsonLdContexts = tasks.register("generateBundledJsonLdContexts") {
    val provenanceFile = layout.projectDirectory.file("jsonld/provenance.json")
    val contextDir = layout.projectDirectory.dir("jsonld/contexts")
    val outputDir = layout.buildDirectory.dir("generated/jsonld/kotlin/id/walt/dcql/jsonld")
    inputs.file(provenanceFile)
    inputs.dir(contextDir)
    outputs.dir(outputDir)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val provenance = groovy.json.JsonSlurper().parse(provenanceFile.asFile) as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val documents = provenance["documents"] as List<Map<String, Any>>
        val generated = StringBuilder()
        generated.appendLine("// Generated from jsonld/contexts by generateBundledJsonLdContexts. Do not edit.")
        generated.appendLine("package id.walt.dcql.jsonld")
        generated.appendLine()
        generated.appendLine("internal object GeneratedBundledContexts {")
        for (document in documents) {
            val fileName = document["file"] as String
            val constant = document["constant"] as String
            val expected = document["sha256"] as String
            val bytes = contextDir.file(fileName).asFile.readBytes()
            val text = bytes.toString(Charsets.UTF_8)
            val actual = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }
            require(actual == expected) {
                "$fileName sha256 $actual does not match jsonld/provenance.json. " +
                    "Refresh with python3 jsonld/vendor_contexts.py."
            }
            require("\"\"\"" !in text && "\$" !in text) {
                "$fileName cannot be embedded as a Kotlin raw string"
            }
            generated.appendLine("    internal const val ${constant}_SHA256: String = \"$expected\"")
            generated.append("    internal val $constant: String = \"\"\"")
            generated.append(text)
            generated.appendLine("\"\"\"")
            generated.appendLine()
        }
        generated.appendLine("}")
        val output = outputDir.get().asFile
        output.mkdirs()
        output.resolve("GeneratedBundledContexts.kt").writeText(generated.toString())
    }
}

tasks.configureEach {
    if (name.startsWith("compileKotlin")) {
        dependsOn(generateBundledJsonLdContexts)
    }
}

kotlin {
    js {
        outputModuleName = "dcql"
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/jsonld/kotlin"))
            dependencies {
                // JSON
                implementation(identityLibs.kotlinx.serialization.json)

                // Coroutines
                implementation(identityLibs.kotlinx.coroutines.core)

                // Logging
                implementation(identityLibs.oshai.kotlinlogging)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(identityLibs.slf4j.simple)
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id DCQL library")
        description.set(
            """
            Kotlin/Java library for DCQL matching
            """.trimIndent()
        )
    }
}
