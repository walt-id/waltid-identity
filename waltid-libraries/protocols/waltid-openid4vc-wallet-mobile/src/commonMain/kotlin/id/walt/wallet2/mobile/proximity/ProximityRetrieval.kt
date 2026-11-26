package id.walt.wallet2.mobile.proximity

// One session probes a BLE role/bearer policy once and may prepare distinct endpoints for its routes.
internal fun requireSharedBlePolicy(
    nfc: ProximityBleConfiguration?, qr: ProximityBleConfiguration?,
) { require(nfc == null || qr == null || nfc == qr) { "QR and NFC must use the same BLE role and bearer policy" } }

internal val ProximitySessionConfiguration.qrRetrieval: ProximityRetrievalOptions?
    get() = when (this) {
        is ProximitySessionConfiguration.Qr -> retrieval
        is ProximitySessionConfiguration.ConventionalNfc -> qrFallback
        is ProximitySessionConfiguration.ProvisionalNfcV2 -> qrFallback
    }

internal val ProximitySessionConfiguration.nfcRetrieval: ProximityRetrievalOptions?
    get() = (this as? ProximitySessionConfiguration.ConventionalNfc)?.retrieval

internal val ProximitySessionConfiguration.nfcBle: ProximityBleConfiguration?
    get() = when (this) {
        is ProximitySessionConfiguration.Qr -> null
        is ProximitySessionConfiguration.ConventionalNfc -> retrieval.bluetoothLowEnergy
        is ProximitySessionConfiguration.ProvisionalNfcV2 -> bluetoothLowEnergy
    }

internal val ProximitySessionConfiguration.nfcWifiAware: Boolean
    get() = when (this) {
        is ProximitySessionConfiguration.Qr -> false
        is ProximitySessionConfiguration.ConventionalNfc -> retrieval.wifiAware
        is ProximitySessionConfiguration.ProvisionalNfcV2 -> wifiAware
    }

internal val ProximitySessionConfiguration.wifiAwareSelected: Boolean
    get() = nfcWifiAware || qrRetrieval?.wifiAware == true

internal val ProximitySessionConfiguration.bleConfiguration: ProximityBleConfiguration?
    get() = nfcBle ?: qrRetrieval?.bluetoothLowEnergy
