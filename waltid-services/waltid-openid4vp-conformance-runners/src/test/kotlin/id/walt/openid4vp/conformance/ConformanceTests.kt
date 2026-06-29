@file:Suppress("DEPRECATION")

package id.walt.openid4vp.conformance

/**
 * @deprecated Use [Verifier2ConformanceTests] instead.
 * This class is kept for backward compatibility with CI scripts.
 */
@Deprecated(
    message = "Use Verifier2ConformanceTests instead",
    replaceWith = ReplaceWith("Verifier2ConformanceTests")
)
class ConformanceTests : Verifier2ConformanceTests()
