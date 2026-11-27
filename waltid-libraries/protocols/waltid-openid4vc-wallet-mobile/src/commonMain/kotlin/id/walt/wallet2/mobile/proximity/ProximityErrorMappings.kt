package id.walt.wallet2.mobile.proximity

import id.walt.mdoc.proximity.ProximityError as EngineProximityError

internal fun String.toRemediationActions(): List<ProximityRemediationAction> = when (this) {
    "ble_permission_missing",
    "ble_permission_not_determined" -> listOf(ProximityRemediationAction.RequestBluetoothPermission)
    "ble_permission_denied",
    "ble_permission_restricted" -> listOf(ProximityRemediationAction.OpenApplicationSettings)
    "ble_powered_off" -> listOf(ProximityRemediationAction.EnableBluetooth)
    "ble_unsupported",
    "ble_scanner_unavailable",
    "ble_advertiser_unavailable",
    "ble_transport_unavailable" -> listOf(ProximityRemediationAction.UseSupportedDevice)
    "ble_state_unknown" -> listOf(ProximityRemediationAction.Retry)
    "nfc_powered_off" -> listOf(ProximityRemediationAction.EnableNfc)
    "nfc_hce_unsupported",
    "nfc_adapter_unavailable",
    "nfc_host_unavailable",
    "nfc_card_session_unsupported" -> listOf(ProximityRemediationAction.UseSupportedDevice)
    "nfc_access_not_accepted",
    "nfc_system_ineligible" -> listOf(ProximityRemediationAction.OpenApplicationSettings)
    "nfc_system_unavailable",
    "nfc_session_already_active",
    "nfc_foreground_routing_required",
    "nfc_card_session_active",
    "nfc_session_expired" -> listOf(ProximityRemediationAction.Retry)
    "wifi_aware_nearby_permission_missing" ->
        listOf(ProximityRemediationAction.RequestNearbyWifiPermission)
    "wifi_aware_local_network_permission_missing" ->
        listOf(ProximityRemediationAction.RequestLocalNetworkPermission)
    "wifi_aware_radio_unavailable" -> listOf(ProximityRemediationAction.EnableWifi)
    "wifi_aware_api_unsupported",
    "wifi_aware_feature_missing",
    "wifi_aware_manager_unavailable",
    "wifi_aware_ncs_sk_128_unsupported",
    "wifi_aware_bands_unavailable",
    "wifi_aware_platform_unsupported",
    "wifi_aware_ios_api_unsupported" -> listOf(ProximityRemediationAction.UseSupportedDevice)
    "wifi_aware_characteristics_unavailable",
    "wifi_aware_resources_unavailable",
    "wifi_aware_capability_check_failed" -> listOf(ProximityRemediationAction.Retry)
    else -> emptyList()
}

internal fun EngineProximityError.toWalletError(): ProximityError = ProximityError(
    category = when (code) {
        "changed_submission", "stale_consent", "stale_submission" ->
            ProximityErrorCategory.StaleSubmission
        "engagement_timeout" -> ProximityErrorCategory.Engagement
        "invalid_reader_authentication" -> ProximityErrorCategory.ReaderAuthentication
        "reader_revoked", "trusted_reader_required" -> ProximityErrorCategory.Trust
        "credential_unavailable", "request_unsatisfied" -> ProximityErrorCategory.Credential
        "holder_key_unavailable" -> ProximityErrorCategory.HolderKey
        "application_profile_rejected",
        "application_profile_unsatisfied",
        "application_profile_failed",
        "application_profile_ambiguous",
        "application_profile_invalid" ->
            ProximityErrorCategory.ApplicationProfile
        else -> when (this) {
            is EngineProximityError.Capability -> ProximityErrorCategory.Capability
            is EngineProximityError.Transport -> ProximityErrorCategory.Transport
            is EngineProximityError.Protocol, is EngineProximityError.Security -> ProximityErrorCategory.Protocol
            is EngineProximityError.Policy -> ProximityErrorCategory.Policy
        }
    },
    code = code,
    message = message,
    recovery = if (this is EngineProximityError.Transport || this is EngineProximityError.Capability || code in setOf("request_processing_failed", "response_processing_failed", "changed_submission", "stale_consent", "stale_submission")) {
        ProximityRecovery.StartNewSession
    } else {
        ProximityRecovery.None
    },
)

internal fun rejectedAction(code: String, message: String): ProximityActionResult.Rejected =
    ProximityActionResult.Rejected(
        ProximityError(
            ProximityErrorCategory.Policy,
            code,
            message,
            recovery = ProximityRecovery.None,
        )
    )
