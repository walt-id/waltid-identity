@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("multiplatform")
}

// KGP can infer ABI for registered, host-unsupported targets, but not for omitted targets.
// Check the enabled targets against a projection of the single, complete committed baseline.
if (!enableIosBuild) {
    val prepareNonIosAbiReference by tasks.registering(PrepareNonIosAbiReference::class) {
        referenceDirectory.set(layout.projectDirectory.dir("api"))
        outputDirectory.set(layout.buildDirectory.dir("abi-reference/non-ios"))
    }
    kotlin.abiValidation {
        referenceDumpDir.set(prepareNonIosAbiReference.flatMap { it.outputDirectory })
        updateTaskProvider.configure {
            doFirst {
                error("Update the complete ABI baseline with -PenableIosBuild=true on macOS.")
            }
        }
    }
}
