import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
    id("waltid.publish.npm")
}

group = "id.walt.dcql"

val generateBundledJsonLdContexts = tasks.register<GenerateBundledJsonLdContexts>("generateBundledJsonLdContexts") {
    provenanceFile.set(layout.projectDirectory.file("jsonld/provenance.json"))
    contextDir.set(layout.projectDirectory.dir("jsonld/contexts"))
    outputDir.set(layout.buildDirectory.dir("generated/jsonld/kotlin"))
}

kotlin {
    js {
        outputModuleName = "dcql"
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBundledJsonLdContexts.flatMap { it.outputDir })
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

abstract class GenerateBundledJsonLdContexts : DefaultTask() {
    @get:InputFile
    abstract val provenanceFile: RegularFileProperty

    @get:InputDirectory
    abstract val contextDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val provenance = groovy.json.JsonSlurper().parse(provenanceFile.get().asFile) as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val documents = provenance["documents"] as List<Map<String, Any>>
        val generated = StringBuilder()
        generated.appendLine("// Generated from jsonld/contexts by generateBundledJsonLdContexts. Do not edit.")
        generated.appendLine("package id.walt.dcql.jsonld")
        generated.appendLine()
        generated.appendLine("internal object GeneratedBundledContexts {")
        val contexts = contextDir.get()
        for (document in documents) {
            val fileName = document["file"] as String
            val constant = document["constant"] as String
            val expected = document["sha256"] as String
            val bytes = contexts.file(fileName).asFile.readBytes()
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
        val destination = outputDir.get().asFile.resolve("id/walt/dcql/jsonld")
        destination.mkdirs()
        destination.resolve("GeneratedBundledContexts.kt").writeText(generated.toString())
    }
}
