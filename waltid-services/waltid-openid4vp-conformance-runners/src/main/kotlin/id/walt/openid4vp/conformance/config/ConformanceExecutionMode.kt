package id.walt.openid4vp.conformance.config

/** Full-plan gap runs are opt-in and must not inherit CI's module slices. */
enum class ConformanceExecutionMode(val value: String) {
    CI("ci"),
    FULL_PLAN("full-plan");

    companion object {
        val current: ConformanceExecutionMode
            get() = fromEnvironment(System.getenv())

        fun fromEnvironment(environment: Map<String, String>): ConformanceExecutionMode {
            val configured = environment["CONFORMANCE_EXECUTION_MODE"]?.trim().orEmpty()
            val mode = entries.singleOrNull { it.value == configured.ifEmpty { "ci" } }
                ?: error("CONFORMANCE_EXECUTION_MODE must be ci or full-plan")
            if (mode == FULL_PLAN) {
                val filters = listOf(
                    "OPENID4VCI_CONFORMANCE_MODULES",
                    "OPENID4VCI_CONFORMANCE_EXCLUDED_MODULES",
                    "OPENID4VCI_CONFORMANCE_ADDITIONAL_MODULES",
                ).filter { !environment[it].isNullOrBlank() }
                require(filters.isEmpty()) { "Full-plan mode forbids module filters: ${filters.joinToString()}" }
                require(environment["OPENID4VCI_CONFORMANCE_MODULE_GROUPS"].orEmpty().trim() in setOf("", "all")) {
                    "Full-plan mode requires all module groups"
                }
                val encryption = environment["OPENID4VCI_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION"].orEmpty()
                    .split(',').map(String::trim).filter(String::isNotEmpty).toSet()
                require(encryption.isEmpty() || encryption == setOf("plain", "encrypted")) {
                    "Full-plan mode runs all suite-defined credential encryption variants"
                }
                require(!environment["OPENID4VCI_CONFORMANCE_ISSUANCE_MODE"]?.trim().equals("deferred", true)) {
                    "The deferred runner is a module slice, not a full-plan run"
                }
                require(!environment["OPENID4VCI_CONFORMANCE_DISCOVERY_ONLY"]?.trim().equals("true", true) &&
                    !environment["OPENID4VCI_CONFORMANCE_MATRIX"]?.trim().equals("discovery", true)) {
                    "Full-plan mode requires live execution, not discovery-only configuration"
                }
            }
            return mode
        }
    }
}
