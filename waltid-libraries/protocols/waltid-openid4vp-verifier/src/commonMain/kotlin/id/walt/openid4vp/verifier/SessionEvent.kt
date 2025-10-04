package id.walt.openid4vp.verifier

@Suppress("EnumEntryName")
    enum class SessionEvent {
        attempted_presentation,
        policy_results_available,
        dcql_fulfillment_check_failed,
        presentation_validation_failed
    }
