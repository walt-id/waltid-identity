@file:OptIn(ExperimentalAbiValidation::class, ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.abi.BinariesSource
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

abstract class CheckTypeScriptBaseline : DefaultTask() {
    @get:InputFile
    abstract val baseline: RegularFileProperty

    @get:InputFile
    abstract val generated: RegularFileProperty

    @TaskAction
    fun checkBaseline() {
        val expected = baseline.get().asFile.readLines().joinToString("\n")
        val actual = generated.get().asFile.readLines().joinToString("\n")
        check(expected == actual) {
            "Generated TypeScript declarations differ from api/waltid-crypto2.d.ts"
        }
    }
}

abstract class CheckSerializationApiPublication : DefaultTask() {
    @get:InputFile
    abstract val jvmModuleMetadata: RegularFileProperty

    @TaskAction
    fun checkPublication() {
        val metadata = jvmModuleMetadata.get().asFile.readText()
        val apiVariant = metadata.substringAfter("\"name\": \"jvmApiElements-published\"", missingDelimiterValue = "")
            .substringBefore("\"name\": \"jvmRuntimeElements-published\"")
        check(apiVariant.isNotEmpty() && "\"module\": \"kotlinx-serialization-json\"" in apiVariant) {
            "kotlinx-serialization-json is missing from published JVM API metadata"
        }
    }
}

val chromeExecutable = providers.environmentVariable("CHROME_BIN").orNull
    ?: listOf("/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser")
        .firstOrNull { file(it).isFile }

plugins {
    id("waltid.full.library")
    id("waltid.publish.maven")
}

group = "id.walt.crypto2"


kotlin {
    abiValidation {
        binariesSource.set(BinariesSource.MAIN_COMPILATION)
    }

    js {
        outputModuleName = "waltid-crypto2"
        browser {
            testTask {
                enabled = true
                chromeExecutable?.let { environment("CHROME_BIN", it) }
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

    wasmJs {
        browser {
            testTask {
                enabled = chromeExecutable != null
                chromeExecutable?.let { environment("CHROME_BIN", it) }
                useKarma { useChromeHeadless() }
            }
        }
        nodejs()
    }

    linuxX64()
    linuxArm64()
    mingwX64()
    macosX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(identityLibs.cryptography.provider.optimal)
            api(identityLibs.kotlinx.serialization.json)
            implementation(identityLibs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(identityLibs.cryptography.provider.jdk)
            implementation(identityLibs.bcprov.jdk18on)
        }

        val opensslMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(identityLibs.cryptography.provider.openssl3.prebuilt)
            }
        }
        linuxMain.get().dependsOn(opensslMain)
        mingwMain.get().dependsOn(opensslMain)

        val opensslTest by creating {
            dependsOn(commonTest.get())
        }
        linuxX64Test.get().dependsOn(opensslTest)
        mingwX64Test.get().dependsOn(opensslTest)
    }
}

val jvmTestCompilation = kotlin.targets.getByName("jvm").compilations.getByName("test")

tasks.register<JavaExec>("benchmarkStoredKeys") {
    group = "benchmark"
    description = "Benchmarks stored-key decoding, restoration, materialization, and cached signing"
    dependsOn(jvmTestCompilation.compileTaskProvider)
    classpath(jvmTestCompilation.output.allOutputs, jvmTestCompilation.runtimeDependencyFiles)
    mainClass.set("id.walt.crypto2.StoredKeyBenchmark")
}

val checkTypeScriptDefinitions by tasks.registering(CheckTypeScriptBaseline::class) {
    group = "verification"
    description = "Checks the generated crypto2 TypeScript declarations against the committed baseline"
    dependsOn("jsProductionLibraryCompileSync")
    baseline.set(layout.projectDirectory.file("api/waltid-crypto2.d.ts"))
    generated.set(layout.buildDirectory.file("compileSync/js/main/productionLibrary/kotlin/waltid-crypto2.d.ts"))
}

val checkSerializationApiPublication by tasks.registering(CheckSerializationApiPublication::class) {
    group = "verification"
    description = "Checks that kotlinx.serialization is published as part of the crypto2 API"
    dependsOn("generateMetadataFileForJvmPublication")
    jvmModuleMetadata.set(layout.buildDirectory.file("publications/jvm/module.json"))
}

tasks.named("check") {
    dependsOn(checkSerializationApiPublication, checkTypeScriptDefinitions)
}

mavenPublishing {
    pom {
        name.set("walt.id crypto2")
        description.set("Next generation cryptography primitives for walt.id")
    }
}
