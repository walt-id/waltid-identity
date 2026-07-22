@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

abstract class ProvisionSoftHsmExampleTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val executablePath: Property<String>

    @get:Input
    @get:Optional
    abstract val libraryPath: Property<String>

    @get:OutputDirectory
    abstract val stateDirectory: DirectoryProperty

    @TaskAction
    fun provision() {
        val executable = executablePath.orNull
        val library = libraryPath.orNull
        if (executable == null || library == null) {
            throw GradleException(
                "SoftHSM 2 not found. Install 'softhsm2' with apt or 'softhsm' with Homebrew, then retry.",
            )
        }

        val state = stateDirectory.get().asFile.apply { mkdirs() }
        val tokens = state.resolve("tokens").apply { mkdirs() }
        val config = state.resolve("softhsm2.conf")
        config.writeText(
            """
            directories.tokendir = ${tokens.absolutePath}
            objectstore.backend = file
            log.level = ERROR
            slots.removable = false
            """.trimIndent(),
        )
        val marker = state.resolve("initialized")
        if (marker.isFile && tokens.listFiles()?.isNotEmpty() == true) return

        val output = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(
                executable,
                "--init-token",
                "--free",
                "--label",
                DEMO_TOKEN_LABEL,
                "--so-pin",
                DEMO_SO_PIN,
                "--pin",
                DEMO_USER_PIN,
            )
            environment("SOFTHSM2_CONF", config.absolutePath)
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException("SoftHSM token initialization failed: ${output.toString().trim()}")
        }
        marker.writeText("initialized")
    }

    companion object {
        const val DEMO_TOKEN_LABEL = "waltid-crypto2-example"
        const val DEMO_SO_PIN = "12345678"
        const val DEMO_USER_PIN = "123456"
    }
}

plugins {
    id("waltid.multiplatform.library.common")
}

group = "id.walt.crypto2.examples"

val chromeExecutable = providers.environmentVariable("CHROME_BIN").orNull
    ?: listOf("/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser")
        .firstOrNull { file(it).isFile }
val softHsmExecutable = listOf(
    "/usr/bin/softhsm2-util",
    "/usr/sbin/softhsm2-util",
    "/usr/local/bin/softhsm2-util",
    "/opt/homebrew/bin/softhsm2-util",
).map(::file).firstOrNull { it.isFile }
val softHsmLibrary = listOf(
    "/usr/lib/softhsm/libsofthsm2.so",
    "/usr/lib/pkcs11/libsofthsm2.so",
    "/usr/lib64/pkcs11/libsofthsm2.so",
    "/usr/local/lib/softhsm/libsofthsm2.so",
    "/usr/local/lib/softhsm/libsofthsm2.dylib",
    "/opt/homebrew/lib/softhsm/libsofthsm2.so",
    "/opt/homebrew/lib/softhsm/libsofthsm2.dylib",
).map(::file).firstOrNull { it.isFile }
val softHsmStateDirectory = layout.buildDirectory.dir("softhsm-example")
val softHsmConfigPath = softHsmStateDirectory.map { it.file("softhsm2.conf").asFile.absolutePath }

kotlin {
    jvm {
        binaries {
            executable {
                mainClass = "id.walt.crypto2.examples.MainKt"
                applicationName = "waltid-crypto2-examples"
            }
        }
    }

    js(IR) {
        outputModuleName = "waltid-crypto2-examples-node"
        useCommonJs()
        nodejs {
            testTask {
                useMocha { timeout = "60s" }
            }
        }
        browser {
            testTask {
                enabled = chromeExecutable != null
                chromeExecutable?.let { environment("CHROME_BIN", it) }
                useKarma { useChromeHeadless() }
            }
        }
        binaries.executable()
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
        binaries.executable()
    }

    linuxX64 {
        binaries.executable {
            baseName = "waltid-crypto2-examples"
            entryPoint = "id.walt.crypto2.examples.main"
        }
    }
    mingwX64 {
        binaries.executable {
            baseName = "waltid-crypto2-examples"
            entryPoint = "id.walt.crypto2.examples.main"
        }
    }
    macosX64 {
        binaries.executable {
            baseName = "waltid-crypto2-examples"
            entryPoint = "id.walt.crypto2.examples.main"
        }
    }
    macosArm64 {
        binaries.executable {
            baseName = "waltid-crypto2-examples"
            entryPoint = "id.walt.crypto2.examples.main"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":waltid-libraries:crypto:waltid-crypto2"))
            implementation(project(":waltid-libraries:crypto:waltid-jose"))
            implementation(identityLibs.kotlinx.coroutines.core)
            implementation(identityLibs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            implementation(project(":waltid-libraries:crypto:waltid-crypto2-pkcs11"))
        }
        jvmTest.dependencies {
            implementation(identityLibs.junit.jupiter.api)
        }

        val didCoseMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(project(":waltid-libraries:crypto:waltid-cose"))
                implementation(project(":waltid-libraries:waltid-did"))
            }
        }
        jvmMain.get().dependsOn(didCoseMain)

        val opensslMain by creating {
            dependsOn(nativeMain.get())
        }
        linuxMain.get().dependsOn(opensslMain)
        mingwMain.get().dependsOn(opensslMain)
    }
}

val provisionSoftHsmExample by tasks.registering(ProvisionSoftHsmExampleTask::class) {
    group = "application"
    description = "Initializes the disposable SoftHSM token used by the PKCS11 example"
    softHsmExecutable?.let { executablePath.set(it.absolutePath) }
    softHsmLibrary?.let { libraryPath.set(it.absolutePath) }
    stateDirectory.set(softHsmStateDirectory)
}

tasks.withType<Test> {
    if (softHsmExecutable != null && softHsmLibrary != null) {
        dependsOn(provisionSoftHsmExample)
        environment("SOFTHSM2_CONF", softHsmConfigPath.get())
        systemProperty("waltid.test.softhsm.library", softHsmLibrary.absolutePath)
        systemProperty("waltid.test.softhsm.executable", softHsmExecutable.absolutePath)
        systemProperty("waltid.test.softhsm.config", softHsmConfigPath.get())
        systemProperty("waltid.test.softhsm.pin", ProvisionSoftHsmExampleTask.DEMO_USER_PIN)
        systemProperty("waltid.test.softhsm.state", softHsmStateDirectory.get().asFile.absolutePath)
    }
}

val jvmMainCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")

tasks.register<JavaExec>("runSoftHsmExample") {
    group = "application"
    description = "Provisions SoftHSM and runs the zero-configuration PKCS11 lifecycle example"
    dependsOn(provisionSoftHsmExample, jvmMainCompilation.compileTaskProvider)
    classpath(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("id.walt.crypto2.examples.MainKt")
    args("pkcs11-softhsm")
    environment("SOFTHSM2_CONF", softHsmConfigPath.get())
    environment("WALTID_SOFTHSM2_PIN", ProvisionSoftHsmExampleTask.DEMO_USER_PIN)
}
