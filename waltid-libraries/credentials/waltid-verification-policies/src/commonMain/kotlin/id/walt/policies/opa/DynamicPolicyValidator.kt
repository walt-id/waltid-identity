package id.walt.policies.opa

import id.walt.policies.policies.DynamicPolicyConfig

class DynamicPolicyValidator(
    private val regoCodeExtractor: DynamicPolicyRegoCodeExtractor,
) {
    suspend fun validate(config: DynamicPolicyConfig) = run {
        validatePolicyName(config.policyName)
        val regoCode = regoCodeExtractor.extract(config)
        validateRegoCode(regoCode)
    }

    private fun validatePolicyName(policyName: String) {
        require(policyName.matches(Regex(POLICY_NAME_REGEX))) {
            "Policy name contains invalid characters."
        }
        require(policyName.length <= MAX_POLICY_NAME_LENGTH) {
            "Policy name exceeds maximum length of $MAX_POLICY_NAME_LENGTH characters"
        }
    }

    private fun validateRegoCode(regoCode: String) {
        require(regoCode.isNotEmpty()) {
            "Rego code cannot be empty"
        }
        require(regoCode.length <= MAX_REGO_CODE_SIZE) {
            "Rego code exceeds maximum allowed size of $MAX_REGO_CODE_SIZE bytes"
        }
    }

    companion object {
        private const val MAX_REGO_CODE_SIZE = 1_000_000 // 1MB limit
        private const val MAX_POLICY_NAME_LENGTH = 64
        private const val POLICY_NAME_REGEX = "^[a-zA-Z_][a-zA-Z0-9_]*$"
    }
}