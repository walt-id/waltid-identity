package id.walt.openid4vp.conformance.report

/**
 * CI / local soft-fail controls for OpenID conformance runners.
 *
 * [CONFORMANCE_ALLOW_FAILURE]:
 * - unset / blank / true / 1 / yes / on → allow failures (record in summary, do not fail JUnit)
 * - false / 0 / no / off → hard-fail when any executed conformance result did not pass
 */
object ConformanceCiFlags {
    const val ALLOW_FAILURE_ENV = "CONFORMANCE_ALLOW_FAILURE"

    fun allowFailure(): Boolean {
        val value = System.getenv(ALLOW_FAILURE_ENV)?.trim()?.lowercase()
        return when {
            value.isNullOrBlank() -> true
            value in setOf("true", "1", "yes", "on") -> true
            value in setOf("false", "0", "no", "off") -> false
            else -> error("Unsupported $ALLOW_FAILURE_ENV value '$value'. Expected true or false.")
        }
    }

    fun strictResults(): Boolean = !allowFailure()
}
