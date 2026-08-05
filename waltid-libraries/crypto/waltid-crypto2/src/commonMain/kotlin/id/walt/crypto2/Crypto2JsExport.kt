@file:OptIn(ExperimentalMultiplatform::class)

package id.walt.crypto2

import kotlin.ExperimentalMultiplatform
import kotlin.OptionalExpectation

/** Keeps object-level JS export metadata while WASM ignores the unsupported annotation. */
@OptionalExpectation
@Target(AnnotationTarget.CLASS)
internal expect annotation class Crypto2JsExport()
