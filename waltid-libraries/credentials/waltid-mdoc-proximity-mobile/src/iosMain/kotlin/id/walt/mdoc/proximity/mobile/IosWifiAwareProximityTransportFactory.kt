package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ReaderSelectedTransportProvider

/** Truthful iOS capability boundary for the currently incompatible public Wi-Fi Aware API. */
public class IosWifiAwareProximityTransportFactory : WifiAwareProximityTransportFactory {
    override suspend fun capability(
        securityPolicy: WifiAwareSecurityPolicy,
    ): WifiAwareProximityAvailability = WifiAwareProximityAvailability.Unavailable(
        implemented = false,
        code = "wifi_aware_ios_api_unsupported",
        message = "iOS Wi-Fi Aware requires prior pairing and a statically declared short service name, which cannot express ISO mdoc transaction-derived discovery",
    )

    override fun create(
        configuration: WifiAwareProximityTransportConfiguration,
    ): ReaderSelectedTransportProvider = error(
        "ISO mdoc Wi-Fi Aware retrieval is not implementable with the current public iOS API",
    )
}
