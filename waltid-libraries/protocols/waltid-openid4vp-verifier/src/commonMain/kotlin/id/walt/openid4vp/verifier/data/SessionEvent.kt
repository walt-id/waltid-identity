package id.walt.openid4vp.verifier.data

@Suppress("EnumEntryName")
enum class SessionEvent {
    authorization_request_requested,
    attempted_presentation,
    parsed_presentation_available,
    validated_credentials_available,
    presentation_fulfils_dcql_query,
    policy_results_available,
    dcql_fulfillment_check_failed,
    presentation_validation_failed
}
