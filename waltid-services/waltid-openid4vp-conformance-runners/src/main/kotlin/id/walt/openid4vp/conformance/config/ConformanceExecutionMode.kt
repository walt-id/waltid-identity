package id.walt.openid4vp.conformance.config

/** Verifier/wallet full-plan gap runs are opt-in and must not inherit CI's module slices. */
enum class ConformanceExecutionMode(val value: String) {
    CI("ci"),
    FULL_PLAN("full-plan");

    companion object {
        val current: ConformanceExecutionMode
            get() = fromEnvironment(System.getenv())

        fun fromEnvironment(environment: Map<String, String>): ConformanceExecutionMode {
            val configured = environment["CONFORMANCE_EXECUTION_MODE"]?.trim().orEmpty()
            return entries.singleOrNull { it.value == configured.ifEmpty { "ci" } }
                ?: error("CONFORMANCE_EXECUTION_MODE must be ci or full-plan")
        }
    }
}
